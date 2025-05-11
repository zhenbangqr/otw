package com.zhenbang.otw.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subTask",
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
data class SubTask(
    @PrimaryKey(autoGenerate = true)
    val subTaskId: Int = 0,
    val subTaskTitle: String,
    val subTaskDesc: String,
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "taskId")
    val taskId: Int,
    val creationTimeStamp: Long = System.currentTimeMillis()
)