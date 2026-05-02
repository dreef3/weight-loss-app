package com.dreef3.weightlossapp.domain.usecase

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
}
