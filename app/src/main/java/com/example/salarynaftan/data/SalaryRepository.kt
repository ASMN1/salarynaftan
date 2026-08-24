package com.example.salarynaftan.data

import android.content.Context
import com.example.salarynaftan.SalaryHistoryRecord
import com.example.salarynaftan.parseMissedDays
import androidx.room.withTransaction

class SalaryRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val monthDao = db.monthSalaryDao()
    private val historyDao = db.historyDao()

    /** Records migrated from v1 whose real year cannot be inferred safely. */
    suspend fun getUnknownYearMonths(): List<MonthSalaryEntity> =
        monthDao.getUnknownYearMonths(AppDatabase.UNKNOWN_YEAR)

    suspend fun getUnknownYearHistory(): List<SalaryHistoryEntity> =
        historyDao.getUnknownYearRecords(AppDatabase.UNKNOWN_YEAR)

    /**
     * Explicitly assigns a user-confirmed year to one legacy month. Existing
     * data is copied before the sentinel row is removed, in one Room transaction.
     */
    suspend fun assignLegacyMonthYear(month: MonthSalaryEntity, year: Int) {
        require(year > 0) { "Legacy year must be positive" }
        db.withTransaction {
            monthDao.upsertMonth(month.copy(year = year))
            monthDao.deleteUnknownYearMonth(AppDatabase.UNKNOWN_YEAR, month.monthIndex)
        }
    }

    suspend fun assignLegacyHistoryYear(record: SalaryHistoryEntity, year: Int) {
        require(year > 0) { "Legacy year must be positive" }
        db.withTransaction {
            historyDao.insertRecord(record.copy(year = year))
            historyDao.deleteUnknownYearRecord(AppDatabase.UNKNOWN_YEAR, record.monthIndex)
        }
    }

    // ===== Month data =====

    suspend fun getMonthData(year: Int, monthIndex: Int): MonthSalaryEntity? {
        return monthDao.getMonth(year, monthIndex)
    }

    suspend fun saveMonthData(entity: MonthSalaryEntity) {
        monthDao.upsertMonth(entity)
    }

    /**
     * Сохраняет месячные поля, не затирая missedDays/vacationDays при гонке.
     * Обычный get+save (в ViewModel) не атомарен: между чтением existing и
     * записью параллельный saveMissedDays может потерять свои данные. Здесь
     * чтение и merge выполняются в одной транзакции Room.
     */
    suspend fun saveMonthPreservingMissed(entity: MonthSalaryEntity) {
        db.withTransaction {
            val existing = monthDao.getMonth(entity.year, entity.monthIndex)
            val merged = entity.copy(
                missedDays = if (entity.missedDays.isBlank()) existing?.missedDays ?: "" else entity.missedDays,
                vacationDays = if (entity.vacationDays.isBlank()) existing?.vacationDays ?: "" else entity.vacationDays
            )
            monthDao.upsertMonth(merged)
        }
    }

    suspend fun getMissedDays(year: Int, monthIndex: Int): Set<Int> {
        val raw = monthDao.getMissedDays(year, monthIndex) ?: ""
        return com.example.salarynaftan.parseMissedDays(raw, year, monthIndex)
    }

    suspend fun getVacationDays(year: Int, monthIndex: Int): Set<Int> {
        val raw = monthDao.getVacationDays(year, monthIndex) ?: ""
        return com.example.salarynaftan.parseMissedDays(raw, year, monthIndex)
    }

    /** Атомарное обновление невыходов — точечный UPDATE без гонки данных. */
    suspend fun saveMissedDays(year: Int, monthIndex: Int, days: Set<Int>) {
        val maxDay = java.time.YearMonth.of(year, monthIndex + 1).lengthOfMonth()
        val raw = days.filter { it in 1..maxDay }.sorted().joinToString(",")
        monthDao.updateMissedDays(year, monthIndex, raw)
    }

    /** Атомарное обновление отпускных дней — точечный UPDATE без гонки данных. */
    suspend fun saveVacationDays(year: Int, monthIndex: Int, days: Set<Int>) {
        val maxDay = java.time.YearMonth.of(year, monthIndex + 1).lengthOfMonth()
        val raw = days.filter { it in 1..maxDay }.sorted().joinToString(",")
        monthDao.updateVacationDays(year, monthIndex, raw)
    }

    // ===== History =====

    // Единый маппинг entity→record (п.3.3 аудита): устранено дублирование DRY.
    private fun SalaryHistoryEntity.toRecord() = SalaryHistoryRecord(
        monthIndex = monthIndex,
        year = year,
        monthName = monthName,
        totalClean = totalClean,
        cleanToPay = cleanToPay,
        advance = advance
    )

    suspend fun getHistoryRecords(): List<SalaryHistoryRecord> =
        historyDao.getAllRecords().map { it.toRecord() }

    suspend fun getHistoryRecordsByYear(year: Int): List<SalaryHistoryRecord> =
        historyDao.getRecordsByYear(year).map { it.toRecord() }

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