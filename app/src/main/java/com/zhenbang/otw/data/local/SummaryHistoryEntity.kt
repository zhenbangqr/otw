package com.zhenbang.otw.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.concurrent.TimeUnit // For default timestamp

@Entity(tableName = "summary_history")
data class SummaryHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val requestJson: String, // The JSON sent to AI
    val aiSummaryResponse: String, // The summary text received from AI
    val savedTimestampMillis: Long = System.currentTimeMillis() // When this record was saved
)