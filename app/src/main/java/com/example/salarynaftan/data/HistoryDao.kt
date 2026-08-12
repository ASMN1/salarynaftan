package com.example.salarynaftan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Query("SELECT * FROM salary_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllRecords(limit: Int = MAX_RECORDS): List<SalaryHistoryEntity>

    @Query("SELECT * FROM salary_history WHERE year = :year ORDER BY monthIndex ASC LIMIT :limit")
    suspend fun getRecordsByYear(year: Int, limit: Int = MAX_RECORDS): List<SalaryHistoryEntity>

    @Query("SELECT DISTINCT year FROM salary_history WHERE year > 0 ORDER BY year DESC LIMIT :limit")
    suspend fun getAvailableYears(limit: Int = MAX_RECORDS): List<Int>

    @Query("SELECT * FROM salary_history WHERE year = :unknownYear ORDER BY monthIndex ASC")
    suspend fun getUnknownYearRecords(unknownYear: Int): List<SalaryHistoryEntity>

    @Query("DELETE FROM salary_history WHERE year = :unknownYear AND monthIndex = :monthIndex")
    suspend fun deleteUnknownYearRecord(unknownYear: Int, monthIndex: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SalaryHistoryEntity)

    @Query("DELETE FROM salary_history WHERE monthIndex = :monthIndex AND year = :year")
    suspend fun deleteRecord(year: Int, monthIndex: Int)

    @Query("DELETE FROM salary_history")
    suspend fun deleteAll()

    companion object {
        /**
         * Верхняя граница записей истории, загружаемых в память. Защищает UI от
         * неограниченного роста списка при многолетней эксплуатации (10 лет ×
         * 12 месяцев ≈ 120 записей, так что 500 — с большим запасом).
         */
        const val MAX_RECORDS = 500
    }
}
