package com.zhenbang.otw.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["departmentId"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("departmentId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val taskId: Int = 0,
    val taskTitle: String = "",
    val taskDescription: String = "",
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "departmentId")
    val departmentId: Int,
    val creationTimestamp: Long = System.currentTimeMillis(),
    val creatorEmail: String? = null,
)