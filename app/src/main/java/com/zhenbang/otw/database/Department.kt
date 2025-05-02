package com.zhenbang.otw.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "departments")
data class Department(
    @PrimaryKey(autoGenerate = true)
    val departmentId: Int = 0,
    val departmentName: String,
    val imageUrl: String? = null,
    val creatorEmail: String? = null,
)

