package com.zhenbang.otw.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "issues",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["departmentId"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.CASCADE // Delete issues when the department is deleted
        )
    ],
    indices = [Index("departmentId")] // Index for faster queries by department
)
data class Issue(
    @PrimaryKey(autoGenerate = true)
    val issueId: Int = 0,
    val issueTitle: String,
    val issueDescription: String,
    @ColumnInfo(name = "departmentId") // Explicitly name the column
    val departmentId: Int,
    val creationTimestamp: Long = System.currentTimeMillis() // Timestamp for sorting
)