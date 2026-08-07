package com.example.salarynaftan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MonthSalaryDao {
    @Query("SELECT * FROM month_salary WHERE year = :year AND monthIndex = :monthIndex")
    suspend fun getMonth(year: Int, monthIndex: Int): MonthSalaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonth(month: MonthSalaryEntity)

    @Query("SELECT missedDays FROM month_salary WHERE year = :year AND monthIndex = :monthIndex")
    suspend fun getMissedDays(year: Int, monthIndex: Int): String?

    @Query("SELECT vacationDays FROM month_salary WHERE year = :year AND monthIndex = :monthIndex")
    suspend fun getVacationDays(year: Int, monthIndex: Int): String?

    /** Атомарное обновление only missedDays — без read-modify-write гонки. */
    @Query("UPDATE month_salary SET missedDays = :raw WHERE year = :year AND monthIndex = :monthIndex")
    suspend fun updateMissedDays(year: Int, monthIndex: Int, raw: String)

    /** Атомарное обновление only vacationDays. */
    @Query("UPDATE month_salary SET vacationDays = :raw WHERE year = :year AND monthIndex = :monthIndex")
    suspend fun updateVacationDays(year: Int, monthIndex: Int, raw: String)
}