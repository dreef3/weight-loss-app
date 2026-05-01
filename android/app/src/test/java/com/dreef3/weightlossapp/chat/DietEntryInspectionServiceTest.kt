package com.dreef3.weightlossapp.chat

import com.dreef3.weightlossapp.domain.model.ConfidenceState
import com.dreef3.weightlossapp.domain.model.ConfirmationStatus
import com.dreef3.weightlossapp.domain.model.FoodEntry
import com.dreef3.weightlossapp.domain.model.FoodEntrySource
import com.dreef3.weightlossapp.domain.model.FoodEntryStatus
import com.dreef3.weightlossapp.domain.repository.FoodEntryRepository
import com.dreef3.weightlossapp.inference.FoodEstimationEngine
import com.dreef3.weightlossapp.inference.FoodEstimationRequest
import com.dreef3.weightlossapp.inference.FoodEstimationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class DietEntryInspectionServiceTest {
    @Test
    fun reestimateEntryPersistsUpdatedCaloriesAndDescription() = runBlocking {
        val photo = kotlin.io.path.createTempFile(suffix = ".jpg").toFile().apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
        val entry = FoodEntry(
            id = 10L,
            capturedAt = Instant.parse("2026-04-19T10:00:00Z"),
            entryDate = LocalDate.parse("2026-04-19"),
            imagePath = photo.absolutePath,
            estimatedCalories = 111,
            finalCalories = 111,
            confidenceState = ConfidenceState.High,
            detectedFoodLabel = "Old name",
            confidenceNotes = null,
            confirmationStatus = ConfirmationStatus.NotRequired,
            source = FoodEntrySource.AiEstimate,
            entryStatus = FoodEntryStatus.Ready,
        )
        val repository = InspectionFakeFoodEntryRepository(listOf(entry))
        val service = DietEntryInspectionService(
            foodEntryRepository = repository,
            foodEstimationEngine = object : FoodEstimationEngine {
                override suspend fun estimate(request: FoodEstimationRequest): Result<FoodEstimationResult> = Result.success(
                    FoodEstimationResult(
                        estimatedCalories = 555,
                        confidenceState = ConfidenceState.High,
                        detectedFoodLabel = request.preferredDescription,
                        confidenceNotes = "Updated by deterministic test",
                    ),
                )
            },
        )

        val result = service.reestimateEntry(
            entryId = 10L,
            correctedDescription = "Risotto with pear and gorgonzola",
            reason = "re-estimate this saved photo",
        )

        assertEquals(true, result["success"])
        val updated = repository.savedEntries.first { it.id == 10L }
        assertEquals(555, updated.finalCalories)
        assertEquals(555, updated.estimatedCalories)
        assertEquals("Risotto with pear and gorgonzola", updated.detectedFoodLabel)
        assertEquals(FoodEntrySource.AiEstimate, updated.source)
        assertEquals(FoodEntryStatus.Ready, updated.entryStatus)
    }

    @Test
    fun reestimateEntryFailsWithoutSavedPhoto() = runBlocking {
        val entry = FoodEntry(
            id = 11L,
            capturedAt = Instant.parse("2026-04-19T10:00:00Z"),
            entryDate = LocalDate.parse("2026-04-19"),
            imagePath = "",
            estimatedCalories = 100,
            finalCalories = 100,
            confidenceState = ConfidenceState.High,
            detectedFoodLabel = "Meal",
            confidenceNotes = null,
            confirmationStatus = ConfirmationStatus.NotRequired,
            source = FoodEntrySource.UserCorrected,
            entryStatus = FoodEntryStatus.Ready,
        )
        val service = DietEntryInspectionService(
            foodEntryRepository = InspectionFakeFoodEntryRepository(listOf(entry)),
            foodEstimationEngine = object : FoodEstimationEngine {
                override suspend fun estimate(request: FoodEstimationRequest): Result<FoodEstimationResult> {
                    error("Should not be called")
                }
            },
        )

        val result = service.reestimateEntry(entryId = 11L, correctedDescription = null, reason = null)

        assertEquals(false, result["success"])
        assertTrue((result["message"] as String).contains("no saved photo", ignoreCase = true))
    }
}

private class InspectionFakeFoodEntryRepository(
    initialEntries: List<FoodEntry>,
) : FoodEntryRepository {
    val savedEntries = initialEntries.toMutableList()
    private val flow = MutableStateFlow(initialEntries)

    override fun observeEntriesFor(date: LocalDate): Flow<List<FoodEntry>> = flow
    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<FoodEntry>> = flow
    override fun observeAllEntries(): Flow<List<FoodEntry>> = flow
    override fun observeEntry(entryId: Long): Flow<FoodEntry?> = MutableStateFlow(savedEntries.firstOrNull { it.id == entryId })
    override suspend fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate): List<FoodEntry> = savedEntries
    override suspend fun getEntry(entryId: Long): FoodEntry? = savedEntries.firstOrNull { it.id == entryId }
    override suspend fun getPendingModelImprovementUploads(): List<FoodEntry> = emptyList()
    override suspend fun markModelImprovementUploaded(entryId: Long, uploadedAt: Instant) = Unit
    override suspend fun resetModelImprovementUploadsSince(cutoff: Instant): Int = 0
    override suspend fun upsert(entry: FoodEntry): Long {
        val id = if (entry.id == 0L) (savedEntries.maxOfOrNull { it.id } ?: 0L) + 1L else entry.id
        savedEntries.removeAll { it.id == id }
        savedEntries += entry.copy(id = id)
        flow.value = savedEntries.toList()
        return id
    }
    override suspend fun delete(entry: FoodEntry) = Unit
}
