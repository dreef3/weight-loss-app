package com.dreef3.weightlossapp.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.dreef3.weightlossapp.domain.usecase.EngineTaskQueue

class WorkManagerEngineTaskQueue(
    private val context: Context,
) : EngineTaskQueue {
    override fun enqueuePhotoEstimate(
        entryId: Long,
        imagePath: String,
        capturedAtEpochMs: Long,
        sessionId: Long?,
        userVisibleText: String?,
        preferredDescription: String?,
    ) {
        enqueue(
            Data.Builder()
                .putString(PersistentEngineTaskWorker.KEY_TASK_TYPE, PersistentEngineTaskWorker.TASK_TYPE_PHOTO_ESTIMATE)
                .putLong(PersistentEngineTaskWorker.KEY_ENTRY_ID, entryId)
                .putString(PersistentEngineTaskWorker.KEY_IMAGE_PATH, imagePath)
                .putLong(PersistentEngineTaskWorker.KEY_CAPTURED_AT_EPOCH_MS, capturedAtEpochMs)
                .putLong(PersistentEngineTaskWorker.KEY_SESSION_ID, sessionId ?: 0L)
                .putString(PersistentEngineTaskWorker.KEY_USER_VISIBLE_TEXT, userVisibleText)
                .putString(PersistentEngineTaskWorker.KEY_PREFERRED_DESCRIPTION, preferredDescription)
                .build(),
        )
    }

    override fun enqueueChatReply(
        sessionId: Long,
        triggerMessageId: Long,
        userVisibleText: String,
        actualPrompt: String,
    ) {
        enqueue(
            Data.Builder()
                .putString(PersistentEngineTaskWorker.KEY_TASK_TYPE, PersistentEngineTaskWorker.TASK_TYPE_CHAT_REPLY)
                .putLong(PersistentEngineTaskWorker.KEY_SESSION_ID, sessionId)
                .putLong(PersistentEngineTaskWorker.KEY_TRIGGER_MESSAGE_ID, triggerMessageId)
                .putString(PersistentEngineTaskWorker.KEY_USER_VISIBLE_TEXT, userVisibleText)
                .putString(PersistentEngineTaskWorker.KEY_ACTUAL_PROMPT, actualPrompt)
                .build(),
        )
    }

    private fun enqueue(inputData: Data) {
        val request = OneTimeWorkRequestBuilder<PersistentEngineTaskWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private companion object {
        private const val UNIQUE_WORK_NAME = "persistent-engine-task-queue"
    }
}
