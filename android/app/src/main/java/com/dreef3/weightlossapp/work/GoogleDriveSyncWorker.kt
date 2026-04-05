package com.dreef3.weightlossapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dreef3.weightlossapp.app.di.AppContainer

class GoogleDriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AppContainer.initialize(applicationContext)
        return AppContainer.instance.googleDriveSyncManager.tryUploadFromWorker().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if ((it.message ?: "").contains("requires user interaction", ignoreCase = true)) {
                    Result.success()
                } else {
                    Result.retry()
                }
            },
        )
    }
}
