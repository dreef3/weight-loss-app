package com.dreef3.weightlossapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dreef3.weightlossapp.data.local.entity.DailyCalorieBudgetPeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCalorieBudgetPeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: DailyCalorieBudgetPeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<DailyCalorieBudgetPeriodEntity>)

    @Query("SELECT * FROM budget_period ORDER BY effectiveFromDateIso ASC")
    fun observeAll(): Flow<List<DailyCalorieBudgetPeriodEntity>>

    @Query("SELECT * FROM budget_period ORDER BY effectiveFromDateIso ASC")
    suspend fun getAll(): List<DailyCalorieBudgetPeriodEntity>

    @Query("SELECT * FROM budget_period WHERE effectiveFromDateIso <= :dateIso ORDER BY effectiveFromDateIso DESC LIMIT 1")
    suspend fun findForDate(dateIso: String): DailyCalorieBudgetPeriodEntity?
}
