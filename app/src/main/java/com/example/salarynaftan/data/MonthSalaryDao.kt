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

    @Query("INSERT INTO month_salary (year, monthIndex, normHours, zaOtsutstvuushego, kvartalka, gazetaInput, pozhertvovanjaInput, subbotnikInput, mmDetiCountInput, childrenCountInput, stravitaInput, inyeVyplatyInput, inyeUderzhanijaInput, missedDays, vacationDays) VALUES (:year, :monthIndex, '', '', '', '0', '0', '0', '0', '0', '0', '0', '0', :raw, '') ON CONFLICT(year, monthIndex) DO UPDATE SET missedDays = excluded.missedDays")
    suspend fun updateMissedDays(year: Int, monthIndex: Int, raw: String)

    @Query("INSERT INTO month_salary (year, monthIndex, normHours, zaOtsutstvuushego, kvartalka, gazetaInput, pozhertvovanjaInput, subbotnikInput, mmDetiCountInput, childrenCountInput, stravitaInput, inyeVyplatyInput, inyeUderzhanijaInput, missedDays, vacationDays) VALUES (:year, :monthIndex, '', '', '', '0', '0', '0', '0', '0', '0', '0', '0', '', :raw) ON CONFLICT(year, monthIndex) DO UPDATE SET vacationDays = excluded.vacationDays")
    suspend fun updateVacationDays(year: Int, monthIndex: Int, raw: String)

    @Query("SELECT * FROM month_salary WHERE year = :unknownYear ORDER BY monthIndex")
    suspend fun getUnknownYearMonths(unknownYear: Int): List<MonthSalaryEntity>

    @Query("DELETE FROM month_salary WHERE year = :unknownYear AND monthIndex = :monthIndex")
    suspend fun deleteUnknownYearMonth(unknownYear: Int, monthIndex: Int)
}