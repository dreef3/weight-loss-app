package com.dreef3.weightlossapp.app.training

import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Base64
import com.dreef3.weightlossapp.BuildConfig
import com.dreef3.weightlossapp.data.preferences.AppPreferences
import com.dreef3.weightlossapp.domain.model.FoodEntry
import com.dreef3.weightlossapp.domain.repository.FoodEntryRepository
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ModelImprovementUploader(
    private val context: Context,
    private val preferences: AppPreferences,
    private val foodEntryRepository: FoodEntryRepository,
) {
    private val integrityTokenProviderMutex = Mutex()
    private var integrityTokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    fun isUploadAvailable(): Boolean = availabilityIssue() == null

    fun availabilityDescription(): String =
        availabilityIssue()
            ?: if (BuildConfig.DEBUG && BuildConfig.MODEL_IMPROVEMENT_DEBUG_TOKEN.isNotBlank()) {
                "Debug build uploads use a local debug token instead of Play Integrity."
            } else {
                "When enabled, Zvaka uploads a downscaled meal photo, detected description, and calorie estimate after Play Integrity verification for recognized installs."
            }

    suspend fun uploadIfEnabled(entry: FoodEntry) {
        if (!preferences.trainingDataSharingEnabled.first()) {
            debugLog("uploadIfEnabled skipped entryId=${entry.id} sharing disabled")
            return
        }
        if (BuildConfig.MODEL_IMPROVEMENT_API_BASE_URL.isBlank()) {
            debugLog("uploadIfEnabled skipped entryId=${entry.id} base URL missing")
            return
        }
        if (!isConfiguredForCurrentBuild()) {
            debugLog("uploadIfEnabled skipped entryId=${entry.id} build not configured")
            return
        }
        if (entry.modelImprovementUploadedAt != null) {
            debugLog("uploadIfEnabled skipped entryId=${entry.id} already uploaded")
            return
        }
        debugLog("uploadIfEnabled starting entryId=${entry.id}")
        uploadEntry(entry)
    }

    suspend fun uploadPendingIfEnabled() {
        if (!preferences.trainingDataSharingEnabled.first()) {
            debugLog("uploadPendingIfEnabled skipped sharing disabled")
            return
        }
        if (BuildConfig.MODEL_IMPROVEMENT_API_BASE_URL.isBlank()) {
            debugLog("uploadPendingIfEnabled skipped base URL missing")
            return
        }
        if (!isConfiguredForCurrentBuild()) {
            debugLog("uploadPendingIfEnabled skipped build not configured")
            return
        }
        val pendingEntries = foodEntryRepository.getPendingModelImprovementUploads()
        debugLog("uploadPendingIfEnabled pendingCount=${pendingEntries.size}")
        pendingEntries.forEach { entry ->
            runCatching {
                uploadEntry(entry)
            }.onFailure { throwable ->
                Log.e(TAG, "uploadPendingIfEnabled failed entryId=${entry.id}", throwable)
                if (shouldRetryPendingUploadFailure(throwable)) {
                    throw throwable
                }
            }
        }
    }

    private suspend fun uploadEntry(entry: FoodEntry) {
        val description = entry.detectedFoodLabel?.trim().orEmpty()
        if (description.isBlank() || entry.finalCalories <= 0) {
            debugLog(
                "uploadEntry skipped entryId=${entry.id} descriptionBlank=${description.isBlank()} " +
                    "finalCalories=${entry.finalCalories}",
            )
            return
        }

        val imageBytes = withContext(Dispatchers.IO) {
            loadDownscaledPhoto(entry.imagePath)
        } ?: run {
            debugLog("uploadEntry skipped entryId=${entry.id} photo unavailable path=${entry.imagePath}")
            return
        }

        val capturedAt = entry.capturedAt.toString()
        val entryId = entry.id.toString()
        val confidenceState = entry.confidenceState.name
        val requestHash = computeRequestHash(
            photoBytes = imageBytes,
            description = description,
            calorieEstimate = entry.finalCalories,
            capturedAt = capturedAt,
            entryId = entryId,
            confidenceState = confidenceState,
        )
        val integrityToken = if (BuildConfig.DEBUG && BuildConfig.MODEL_IMPROVEMENT_DEBUG_TOKEN.isNotBlank()) {
            debugLog("uploadEntry using debug auth entryId=${entry.id}")
            null
        } else {
            debugLog("uploadEntry requesting integrity token entryId=${entry.id}")
            requestIntegrityToken(requestHash)
        }

        withContext(Dispatchers.IO) {
            val boundary = "Boundary-${UUID.randomUUID()}"
            val uploadUrl = "${BuildConfig.MODEL_IMPROVEMENT_API_BASE_URL.trimEnd('/')}/v1/examples"
            debugLog("uploadEntry posting entryId=${entry.id} url=$uploadUrl bytes=${imageBytes.size}")
            val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
            }

            DataOutputStream(connection.outputStream).use { output ->
                writeFormField(output, boundary, "description", description)
                writeFormField(output, boundary, "calorie_estimate", entry.finalCalories.toString())
                writeFormField(output, boundary, "captured_at", capturedAt)
                writeFormField(output, boundary, "entry_id", entryId)
                writeFormField(output, boundary, "confidence_state", confidenceState)
                integrityToken?.let { writeFormField(output, boundary, "integrity_token", it) }
                if (BuildConfig.DEBUG && BuildConfig.MODEL_IMPROVEMENT_DEBUG_TOKEN.isNotBlank()) {
                    writeFormField(output, boundary, "debug_auth_token", BuildConfig.MODEL_IMPROVEMENT_DEBUG_TOKEN)
                }
                writeFileField(
                    output = output,
                    boundary = boundary,
                    fieldName = "photo",
                    fileName = "meal-${entry.id.takeIf { it > 0 } ?: entry.capturedAt.toEpochMilli()}.jpg",
                    bytes = imageBytes,
                    contentType = "image/jpeg",
                )
                output.writeBytes("--$boundary--\r\n")
                output.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                Log.e(
                    TAG,
                    "uploadEntry failed entryId=${entry.id} responseCode=$responseCode body=${errorBody.orEmpty().take(400)}",
                )
                error("Training upload failed with HTTP $responseCode${errorBody?.let { ": $it" } ?: ""}")
            }
            debugLog("uploadEntry success entryId=${entry.id} responseCode=$responseCode")
        }
        if (entry.id > 0) {
            foodEntryRepository.markModelImprovementUploaded(entry.id, Instant.now())
            debugLog("uploadEntry marked uploaded entryId=${entry.id}")
        }
    }

    private fun isConfiguredForCurrentBuild(): Boolean = availabilityIssue() == null

    private fun availabilityIssue(): String? {
        if (BuildConfig.MODEL_IMPROVEMENT_API_BASE_URL.isBlank()) {
            return "Model improvement upload is not configured for this build."
        }
        if (BuildConfig.DEBUG && BuildConfig.MODEL_IMPROVEMENT_DEBUG_TOKEN.isNotBlank()) {
            return null
        }
        if (BuildConfig.MODEL_IMPROVEMENT_CLOUD_PROJECT_NUMBER <= 0L) {
            return "Model improvement upload is not configured for this build."
        }
        if (!isInstalledFromGooglePlay()) {
            return "This release install was not installed by Google Play, so Play Integrity-backed uploads are unavailable here."
        }
        return null
    }

    private fun isInstalledFromGooglePlay(): Boolean {
        val installerPackageName = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installerPackageName == PLAY_STORE_PACKAGE_NAME
    }

    private suspend fun requestIntegrityToken(requestHash: String): String {
        val token = runCatching {
            getOrCreateIntegrityTokenProvider().request(createStandardIntegrityTokenRequest(requestHash)).await()
        }.recoverCatching { throwable ->
            val integrityException = throwable as? StandardIntegrityException
            if (integrityException?.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) {
                throw throwable
            }
            invalidateIntegrityTokenProvider()
            getOrCreateIntegrityTokenProvider().request(createStandardIntegrityTokenRequest(requestHash)).await()
        }.getOrThrow()
        return token.token()
    }

    private suspend fun getOrCreateIntegrityTokenProvider(): StandardIntegrityManager.StandardIntegrityTokenProvider {
        integrityTokenProvider?.let { return it }
        return integrityTokenProviderMutex.withLock {
            integrityTokenProvider?.let { return@withLock it }
            IntegrityManagerFactory.createStandard(context).prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(BuildConfig.MODEL_IMPROVEMENT_CLOUD_PROJECT_NUMBER)
                    .build(),
            ).await().also { provider ->
                integrityTokenProvider = provider
            }
        }
    }

    private suspend fun invalidateIntegrityTokenProvider() {
        integrityTokenProviderMutex.withLock {
            integrityTokenProvider = null
        }
    }

    private fun createStandardIntegrityTokenRequest(requestHash: String): StandardIntegrityManager.StandardIntegrityTokenRequest =
        StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
            .setRequestHash(requestHash)
            .build()

    private fun shouldRetryPendingUploadFailure(throwable: Throwable): Boolean {
        val integrityException = throwable as? StandardIntegrityException ?: return false
        return integrityException.errorCode == StandardIntegrityErrorCode.TOO_MANY_REQUESTS ||
            integrityException.errorCode == StandardIntegrityErrorCode.NETWORK_ERROR ||
            integrityException.errorCode == StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE ||
            integrityException.errorCode == StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR ||
            integrityException.errorCode == StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE
    }

    private fun loadDownscaledPhoto(imagePath: String): ByteArray? {
        val file = File(imagePath)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null

        val scaledBitmap = if (bitmap.width > MAX_DIMENSION_PX || bitmap.height > MAX_DIMENSION_PX) {
            val scale = minOf(
                MAX_DIMENSION_PX.toFloat() / bitmap.width.toFloat(),
                MAX_DIMENSION_PX.toFloat() / bitmap.height.toFloat(),
            )
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        return ByteArrayOutputStream().use { stream ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            scaledBitmap.recycle()
            stream.toByteArray()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimensionPx * 2 || currentHeight > maxDimensionPx * 2) {
            sample *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sample
    }

    private fun writeFormField(
        output: DataOutputStream,
        boundary: String,
        fieldName: String,
        value: String,
    ) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n")
        output.writeBytes(value)
        output.writeBytes("\r\n")
    }

    private fun writeFileField(
        output: DataOutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
        output.writeBytes("Content-Type: $contentType\r\n\r\n")
        output.write(bytes)
        output.writeBytes("\r\n")
    }

    private fun computeRequestHash(
        photoBytes: ByteArray,
        description: String,
        calorieEstimate: Int,
        capturedAt: String,
        entryId: String,
        confidenceState: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(photoBytes)
        digest.update(0)
        listOf(
            description,
            calorieEstimate.toString(),
            capturedAt,
            entryId,
            confidenceState,
        ).forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private companion object {
        private const val TAG = "ModelImprovement"
        private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
        const val MAX_DIMENSION_PX = 1024
        const val JPEG_QUALITY = 85
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
