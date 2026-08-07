package com.example.salarynaftan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "salary_history",
    indices = [
        Index(value = ["year", "monthIndex"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class SalaryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val monthIndex: Int,
    val monthName: String,
    val totalClean: Double,
    val cleanToPay: Double,
    val advance: Double,
    val timestamp: Long = System.currentTimeMillis()
)