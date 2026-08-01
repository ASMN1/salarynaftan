package com.example.salarynaftan

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale


// ==========================================
// ИСТОРИЯ РАСЧЁТОВ ЗАРПЛАТЫ
// ==========================================
data class SalaryHistoryRecord(
    val monthIndex: Int,
    val monthName: String,
    val totalClean: Double,
    val cleanToPay: Double,
    val advance: Double
)

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences(PreferenceKeys.SALARY_HISTORY_PREFS, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    fun getRecords(): List<SalaryHistoryRecord> {
        val raw = prefs.getString(PreferenceKeys.KEY_HISTORY_RECORDS, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 5) {
                val idx = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1]
                val total = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val clean = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val adv = parts[4].toDoubleOrNull() ?: return@mapNotNull null
                SalaryHistoryRecord(idx, name, total, clean, adv)
            } else null
        }
    }

    fun saveRecord(monthIndex: Int, monthName: String, totalClean: Double, cleanToPay: Double, advance: Double) {
        val list = getRecords().toMutableList()
        list.removeAll { it.monthIndex == monthIndex }
        list.add(0, SalaryHistoryRecord(monthIndex, monthName, totalClean, cleanToPay, advance))
        saveList(list)
    }

    fun deleteRecord(monthIndex: Int) {
        val list = getRecords().toMutableList()
        list.removeAll { it.monthIndex == monthIndex }
        saveList(list)
    }

    // НОВЫЙ МЕТОД: удалить все записи
    fun deleteAll() {
        prefs.edit().remove(PreferenceKeys.KEY_HISTORY_RECORDS).apply()
    }

    // НОВЫЙ МЕТОД: экспорт в CSV
    fun exportToCsv(): File? {
        val records = getRecords()
        if (records.isEmpty()) return null

        val csvContent = StringBuilder()
        csvContent.append("Месяц;Итого начислено;К выплате;Аванс\n")
        records.forEach { record ->
            csvContent.append("${record.monthName};${String.format(Locale.US, "%.2f", record.totalClean)};${String.format(Locale.US, "%.2f", record.cleanToPay)};${String.format(Locale.US, "%.2f", record.advance)}\n")
        }

        val dir = File(appContext.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, "history_export_${System.currentTimeMillis()}.csv")
        file.writeText(csvContent.toString())
        return file
    }

    // НОВЫЙ МЕТОД: поделиться CSV
    fun shareCsv(context: Context): Boolean {
        val file = exportToCsv() ?: return false
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт истории"))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun saveList(list: List<SalaryHistoryRecord>) {
        val raw = list.joinToString(";") { "${it.monthIndex}|${it.monthName}|${it.totalClean}|${it.cleanToPay}|${it.advance}" }
        prefs.edit().putString(PreferenceKeys.KEY_HISTORY_RECORDS, raw).apply()
    }
}

// ==========================================
// АВТОЗАПОЛНЕНИЕ ЧАСОВ/СМЕН ИЗ ГРАФИКА + ПАРСИНГ ВВОДА
// ==========================================
fun autoFillFromSchedule(context: Context, monthIndex: Int, brigade: Int): Triple<Double, Double, Double> {
    val year = java.time.LocalDate.now().year
    val yearMonth = java.time.YearMonth.of(year, monthIndex + 1)

    var nightCount = 0.0
    var dayCount = 0.0
    var workDaysCount = 0.0

    for (day in 1..yearMonth.lengthOfMonth()) {
        val date = yearMonth.atDay(day)
        val shift = ShiftSchedule.shiftFor(date, brigade)

        when (shift) {
            ShiftType.NIGHT -> {
                nightCount += 1.0
                workDaysCount += 1.0
            }
            ShiftType.DAY -> {
                dayCount += 1.0
                workDaysCount += 1.0
            }
            ShiftType.MORNING -> {
                workDaysCount += 1.0
            }
            ShiftType.OFF -> {}
        }
    }

    val calculatedFactHours = workDaysCount * 8.0
    return Triple(calculatedFactHours, nightCount, dayCount)
}

fun parseNonNegative(input: String): Double =
    input.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0

fun displayInt(input: String): String =
    input.replace(',', '.').toDoubleOrNull()?.toInt()?.toString() ?: input