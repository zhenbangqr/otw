package com.zhenbang.otw.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE) // Ignore if somehow a duplicate appears based on PrimaryKey (unlikely with autoGenerate)
    suspend fun insertSummary(summary: SummaryHistoryEntity)

    @Query("SELECT * FROM summary_history ORDER BY savedTimestampMillis DESC")
    fun getAllSummaries(): Flow<List<SummaryHistoryEntity>>

    // --- NEW: Update Method ---
    // Option A: Update the whole entity (useful if more fields become editable later)
    @Update
    suspend fun updateSummary(summary: SummaryHistoryEntity)

    // Option B: Update only the summary text (simpler if only text is editable)
    // @Query("UPDATE summary_history SET aiSummaryResponse = :newSummary WHERE id = :id")
    // suspend fun updateSummaryText(id: Int, newSummary: String)
    // --- Choose Option A or B --- Let's use Option A (@Update) for flexibility ---

    // --- NEW: Delete Method ---
    // Option A: Delete by entity object
    @Delete
    suspend fun deleteSummary(summary: SummaryHistoryEntity)

    // Option B: Delete by ID
    // @Query("DELETE FROM summary_history WHERE id = :id")
    // suspend fun deleteById(id: Int)
    // --- Choose Option A or B --- Let's use Option A (@Delete) ---
}