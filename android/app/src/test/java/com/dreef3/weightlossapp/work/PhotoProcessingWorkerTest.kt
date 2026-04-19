package com.dreef3.weightlossapp.work

import com.dreef3.weightlossapp.inference.FoodEstimationError
import com.dreef3.weightlossapp.inference.FoodEstimationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class PhotoProcessingWorkerTest {
    @Test
    fun retriesWhenFailureWasCancellation() {
        assertTrue(PhotoProcessingWorker.shouldRetryAfterFailure(CancellationException("cancelled")))
    }

    @Test
    fun retriesWhenFailureWasInferenceTimeout() {
        assertTrue(
            PhotoProcessingWorker.shouldRetryAfterFailure(
                FoodEstimationException(FoodEstimationError.InferenceTimeout),
            ),
        )
    }

    @Test
    fun doesNotRetryForNonRetriableFailure() {
        assertFalse(
            PhotoProcessingWorker.shouldRetryAfterFailure(
                FoodEstimationException(FoodEstimationError.EstimationFailed),
            ),
        )
    }
}
