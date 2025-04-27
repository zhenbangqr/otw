package com.zhenbang.otw.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["departmentId"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.CASCADE // Important:  Delete tasks when the department is deleted
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
    @ColumnInfo(name = "departmentId")  //  Explicitly name the column
    val departmentId: Int,
    val creationTimestamp: Long = System.currentTimeMillis()
)
