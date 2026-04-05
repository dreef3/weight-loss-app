package com.dreef3.weightlossapp.app

import android.util.Log
import com.dreef3.weightlossapp.BuildConfig
import com.dreef3.weightlossapp.app.di.AppContainer
import com.dreef3.weightlossapp.app.media.ModelDescriptors
import com.dreef3.weightlossapp.app.network.NetworkConnectionType
import com.dreef3.weightlossapp.inference.CalorieEstimationModel
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object AppInitializer {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var started = false

    fun initialize(container: AppContainer) {
        if (started) return
        started = true
        executor.execute {
            runCatching {
                container.photoStorage.ensureDirectories()
                container.modelStorage.modelDirectory.mkdirs()
                container.modelStorage.cleanupIncompleteModelFiles(ModelDescriptors.gemma)
                container.modelStorage.logState(tag = TAG, model = ModelDescriptors.gemma)
                runBlocking { container.preferences.setCalorieEstimationModel(CalorieEstimationModel.Gemma) }
                val driveSyncEnabled = runBlocking { container.preferences.readDriveSyncState().isEnabled }
                if (driveSyncEnabled) {
                    container.driveSyncScheduler.enablePeriodicSync()
                } else {
                    container.driveSyncScheduler.disablePeriodicSync()
                }
                val onboardingComplete = runBlocking { container.preferences.hasCompletedOnboarding.first() }
                if (onboardingComplete &&
                    !container.modelStorage.hasUsableModel(ModelDescriptors.gemma) &&
                    container.networkConnectionMonitor.currentConnectionType() == NetworkConnectionType.Wifi
                ) {
                    container.modelDownloadRepository.enqueueIfNeeded(ModelDescriptors.gemma)
                    debugLog("Scheduled Gemma background download on Wi-Fi")
                }
                if (!container.modelStorage.hasUsableModel(ModelDescriptors.gemma)) {
                    debugLog("Skipped model warm-up because Gemma is not ready yet")
                    return@runCatching
                }
                debugLog("Skipping startup Gemma warm-up for now")
            }
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.ENABLE_VERBOSE_LOGGING) {
            Log.i(TAG, message)
        }
    }

    private const val TAG = "AppInitializer"
}
