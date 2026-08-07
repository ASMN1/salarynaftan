package com.example.salarynaftan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Query("SELECT * FROM salary_history ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<SalaryHistoryEntity>

    @Query("SELECT * FROM salary_history WHERE year = :year ORDER BY monthIndex ASC")
    suspend fun getRecordsByYear(year: Int): List<SalaryHistoryEntity>

    @Query("SELECT DISTINCT year FROM salary_history ORDER BY year DESC")
    suspend fun getAvailableYears(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SalaryHistoryEntity)

    @Query("DELETE FROM salary_history WHERE monthIndex = :monthIndex AND year = :year")
    suspend fun deleteRecord(year: Int, monthIndex: Int)

    @Query("DELETE FROM salary_history")
    suspend fun deleteAll()
}
