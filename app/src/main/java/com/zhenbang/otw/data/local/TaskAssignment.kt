package com.zhenbang.otw.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "taskAssignment",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["taskId"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TaskAssignment(
    @PrimaryKey(autoGenerate = true)
    val taskAssignmentId: Int = 0,
    @ColumnInfo(name = "taskId")
    val taskId: Int,
    val userEmail: String,
    val creationTimeStamp: Long = System.currentTimeMillis()
)