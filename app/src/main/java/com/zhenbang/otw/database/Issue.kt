package com.zhenbang.otw.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "issues",
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
data class Issue(
    @PrimaryKey(autoGenerate = true)
    val issueId: Int = 0,
    val issueTitle: String,
    val issueDescription: String,
    @ColumnInfo(name = "departmentId")
    val departmentId: Int,
    val creationTimestamp: Long = System.currentTimeMillis(),
    val creatorEmail: String? = null
)