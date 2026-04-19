package com.dreef3.weightlossapp.chat

import com.dreef3.weightlossapp.app.time.LocalDateProvider
import com.dreef3.weightlossapp.domain.model.FoodEntry
import com.dreef3.weightlossapp.domain.repository.FoodEntryRepository
import com.dreef3.weightlossapp.domain.usecase.UpdateFoodEntryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DietEntryCorrectionServiceTest {
    @Test
    fun logEntryRejectsFutureDate() = runBlocking {
        val repository = CorrectionFakeFoodEntryRepository()
        val service = DietEntryCorrectionService(
            foodEntryRepository = repository,
            updateFoodEntryUseCase = UpdateFoodEntryUseCase(repository),
            localDateProvider = LocalDateProvider(ZoneId.of("UTC")),
        )

        val result = service.logEntry(
            DietEntryLogRequest(
                description = "Burger and fries",
                calories = 700,
                dateIso = "2099-01-01",
                reason = "future date attempt",
            ),
        )

        assertEquals(false, result["success"])
        assertTrue((result["message"] as String).contains("future", ignoreCase = true))
        assertTrue(repository.savedEntries.isEmpty())
    }
}

private class CorrectionFakeFoodEntryRepository : FoodEntryRepository {
    val savedEntries = mutableListOf<FoodEntry>()
    private val flow = MutableStateFlow<List<FoodEntry>>(emptyList())

    override fun observeEntriesFor(date: LocalDate): Flow<List<FoodEntry>> = flow
    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<FoodEntry>> = flow
    override fun observeAllEntries(): Flow<List<FoodEntry>> = flow
    override fun observeEntry(entryId: Long): Flow<FoodEntry?> = MutableStateFlow(savedEntries.firstOrNull { it.id == entryId })
    override suspend fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate): List<FoodEntry> = savedEntries
    override suspend fun getEntry(entryId: Long): FoodEntry? = savedEntries.firstOrNull { it.id == entryId }
    override suspend fun getPendingModelImprovementUploads(): List<FoodEntry> = emptyList()
    override suspend fun markModelImprovementUploaded(entryId: Long, uploadedAt: Instant) = Unit
    override suspend fun upsert(entry: FoodEntry): Long {
        val id = if (entry.id == 0L) (savedEntries.maxOfOrNull { it.id } ?: 0L) + 1L else entry.id
        savedEntries.removeAll { it.id == id }
        savedEntries += entry.copy(id = id)
        flow.value = savedEntries.toList()
        return id
    }
    override suspend fun delete(entry: FoodEntry) = Unit
}
