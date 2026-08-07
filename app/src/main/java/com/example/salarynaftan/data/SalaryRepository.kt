package com.example.salarynaftan.data

import android.content.Context
import com.example.salarynaftan.SalaryHistoryRecord
import com.example.salarynaftan.parseMissedDays

class SalaryRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val monthDao = db.monthSalaryDao()
    private val historyDao = db.historyDao()

    // ===== Month data =====

    suspend fun getMonthData(year: Int, monthIndex: Int): MonthSalaryEntity? {
        return monthDao.getMonth(year, monthIndex)
    }

    suspend fun saveMonthData(entity: MonthSalaryEntity) {
        monthDao.upsertMonth(entity)
    }

    suspend fun getMissedDays(year: Int, monthIndex: Int): Set<Int> {
        val raw = monthDao.getMissedDays(year, monthIndex) ?: ""
        return parseMissedDays(raw)
    }

    suspend fun getVacationDays(year: Int, monthIndex: Int): Set<Int> {
        val raw = monthDao.getVacationDays(year, monthIndex) ?: ""
        return parseMissedDays(raw)
    }

    /** Атомарное обновление невыходов — точечный UPDATE без гонки данных. */
    suspend fun saveMissedDays(year: Int, monthIndex: Int, days: Set<Int>) {
        val raw = days.sorted().joinToString(",")
        monthDao.updateMissedDays(year, monthIndex, raw)
    }

    /** Атомарное обновление отпускных дней — точечный UPDATE без гонки данных. */
    suspend fun saveVacationDays(year: Int, monthIndex: Int, days: Set<Int>) {
        val raw = days.sorted().joinToString(",")
        monthDao.updateVacationDays(year, monthIndex, raw)
    }

    // ===== History =====

    suspend fun getHistoryRecords(): List<SalaryHistoryRecord> {
        return historyDao.getAllRecords().map { entity ->
            SalaryHistoryRecord(
                monthIndex = entity.monthIndex,
                year = entity.year,
                monthName = entity.monthName,
                totalClean = entity.totalClean,
                cleanToPay = entity.cleanToPay,
                advance = entity.advance
            )
        }
    }

    suspend fun getHistoryRecordsByYear(year: Int): List<SalaryHistoryRecord> {
        return historyDao.getRecordsByYear(year).map { entity ->
            SalaryHistoryRecord(
                monthIndex = entity.monthIndex,
                year = entity.year,
                monthName = entity.monthName,
                totalClean = entity.totalClean,
                cleanToPay = entity.cleanToPay,
                advance = entity.advance
            )
        }
    }

    suspend fun getAvailableYears(): List<Int> = historyDao.getAvailableYears()

    suspend fun getHistoryEntities(): List<SalaryHistoryEntity> {
        return historyDao.getAllRecords()
    }

    suspend fun saveHistoryRecord(monthIndex: Int, year: Int, monthName: String, totalClean: Double, cleanToPay: Double, advance: Double) {
        historyDao.insertRecord(
            SalaryHistoryEntity(
                year = year,
                monthIndex = monthIndex,
                monthName = monthName,
                totalClean = totalClean,
                cleanToPay = cleanToPay,
                advance = advance,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistoryRecord(year: Int, monthIndex: Int) {
        historyDao.deleteRecord(year, monthIndex)
    }

    suspend fun deleteAllHistory() {
        historyDao.deleteAll()
    }
}