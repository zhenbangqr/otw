package com.zhenbang.otw.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deptUsers",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["departmentId"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("departmentId"),
        Index(value = ["userEmail", "departmentId"], unique = true)
    ]
)
data class DeptUser(
    @PrimaryKey(autoGenerate = true)
    val deptUserId: Int = 0,
    val userEmail: String,
    @ColumnInfo(name = "departmentId")
    val departmentId: Int,
    val creationTimestamp: Long = System.currentTimeMillis()
)