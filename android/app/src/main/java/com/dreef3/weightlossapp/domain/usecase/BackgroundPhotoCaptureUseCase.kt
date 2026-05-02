package com.dreef3.weightlossapp.domain.usecase

import com.dreef3.weightlossapp.app.time.LocalDateProvider
import com.dreef3.weightlossapp.app.media.PhotoStorage
import com.dreef3.weightlossapp.domain.model.ConfidenceState
import com.dreef3.weightlossapp.domain.model.ConfirmationStatus
import com.dreef3.weightlossapp.domain.model.FoodEntry
import com.dreef3.weightlossapp.domain.model.FoodEntrySource
import com.dreef3.weightlossapp.domain.model.FoodEntryStatus
import com.dreef3.weightlossapp.domain.repository.FoodEntryRepository
import java.time.Instant

class BackgroundPhotoCaptureUseCase(
    private val repository: FoodEntryRepository,
    private val engineTaskQueue: EngineTaskQueue,
    private val localDateProvider: LocalDateProvider,
    private val photoStorage: PhotoStorage,
) {
    suspend fun enqueue(imagePath: String, capturedAt: Instant): Long {
        return enqueueInternal(
            imagePath = imagePath,
            capturedAt = capturedAt,
            sessionId = null,
            userVisibleText = null,
            preferredDescription = null,
        )
    }

    suspend fun enqueueFromChat(
        imagePath: String,
        capturedAt: Instant,
        sessionId: Long,
        userVisibleText: String,
        preferredDescription: String?,
    ): Long {
        return enqueueInternal(
            imagePath = imagePath,
            capturedAt = capturedAt,
            sessionId = sessionId,
            userVisibleText = userVisibleText,
            preferredDescription = preferredDescription,
        )
    }

    private suspend fun enqueueInternal(
        imagePath: String,
        capturedAt: Instant,
        sessionId: Long?,
        userVisibleText: String?,
        preferredDescription: String?,
    ): Long {
        photoStorage.normalizePhoto(imagePath)
        val pendingEntry = FoodEntry(
            capturedAt = capturedAt,
            entryDate = localDateProvider.dateFor(capturedAt),
            imagePath = imagePath,
            estimatedCalories = 0,
            finalCalories = 0,
            confidenceState = ConfidenceState.Failed,
            detectedFoodLabel = preferredDescription,
            confidenceNotes = "Processing photo in background.",
            confirmationStatus = ConfirmationStatus.NotRequired,
            source = FoodEntrySource.AiEstimate,
            entryStatus = FoodEntryStatus.Processing,
        )
        val entryId = repository.upsert(pendingEntry)
        engineTaskQueue.enqueuePhotoEstimate(
            entryId = entryId,
            imagePath = imagePath,
            capturedAtEpochMs = capturedAt.toEpochMilli(),
            sessionId = sessionId,
            userVisibleText = userVisibleText,
            preferredDescription = preferredDescription,
        )
        return entryId
    }

    suspend fun retry(entry: FoodEntry) {
        photoStorage.normalizePhoto(entry.imagePath)
        val retryingEntry = entry.copy(
            estimatedCalories = 0,
            finalCalories = 0,
            confidenceState = ConfidenceState.Failed,
            detectedFoodLabel = entry.detectedFoodLabel,
            confidenceNotes = "Retrying photo estimation in background.",
            confirmationStatus = ConfirmationStatus.NotRequired,
            source = FoodEntrySource.AiEstimate,
            entryStatus = FoodEntryStatus.Processing,
        )
        repository.upsert(retryingEntry)
        engineTaskQueue.enqueuePhotoEstimate(
            entryId = entry.id,
            imagePath = entry.imagePath,
            capturedAtEpochMs = entry.capturedAt.toEpochMilli(),
            sessionId = null,
            userVisibleText = null,
            preferredDescription = entry.detectedFoodLabel,
        )
    }
}
