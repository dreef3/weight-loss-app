package com.dreef3.weightlossapp.domain.usecase

import kotlinx.coroutines.flow.Flow

data class EngineQueueState(
    val totalPendingCount: Int = 0,
    val photoPendingCount: Int = 0,
    val chatPendingCount: Int = 0,
    val sessionPendingCount: Int = 0,
)

interface EngineTaskQueue {
    fun enqueuePhotoEstimate(
        entryId: Long,
        imagePath: String,
        capturedAtEpochMs: Long,
        sessionId: Long? = null,
        userVisibleText: String? = null,
        preferredDescription: String? = null,
    )

    fun enqueueChatReply(
        sessionId: Long,
        triggerMessageId: Long,
        userVisibleText: String,
        actualPrompt: String,
    )

    fun observeState(sessionId: Long? = null): Flow<EngineQueueState>
}
