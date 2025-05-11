package com.zhenbang.otw.data.repository

import com.zhenbang.otw.data.local.SummaryHistoryDao
import com.zhenbang.otw.data.local.SummaryHistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val summaryHistoryDao: SummaryHistoryDao
) {
    val allSummaries: Flow<List<SummaryHistoryEntity>> = summaryHistoryDao.getAllSummaries()

    suspend fun insertSummary(summary: SummaryHistoryEntity) {
        summaryHistoryDao.insertSummary(summary)
    }

    // --- NEW: Update Function ---
    suspend fun updateSummary(summary: SummaryHistoryEntity) {
        summaryHistoryDao.updateSummary(summary)
        // If using Option B in DAO:
        // summaryHistoryDao.updateSummaryText(summary.id, summary.aiSummaryResponse)
    }

    // --- NEW: Delete Function ---
    suspend fun deleteSummary(summary: SummaryHistoryEntity) {
        summaryHistoryDao.deleteSummary(summary)
        // If using Option B in DAO:
        // summaryHistoryDao.deleteById(summary.id)
    }
}