package com.example.salarynaftan

import android.content.Context
import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.export.HistoryExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale


// ==========================================
// ИСТОРИЯ РАСЧЁТОВ ЗАРПЛАТЫ
// ==========================================
data class SalaryHistoryRecord(
    val monthIndex: Int,
    val year: Int,
    val monthName: String,
    val totalClean: Double,
    val cleanToPay: Double,
    val advance: Double
)

// Все операции suspend — выполняются в scope вызывающей стороны (ViewModel /
// Compose-корутина), а не в бессрочном внутреннем CoroutineScope. Так scope
// гарантированно отменяется вместе с экраном и не «живёт» весь процесс.
class HistoryManager(
    private val repository: SalaryRepository,
    private val exporter: HistoryExporter
) {
    private val _records = MutableStateFlow<List<SalaryHistoryRecord>>(emptyList())
    val records: StateFlow<List<SalaryHistoryRecord>> = _records.asStateFlow()

    private val _availableYears = MutableStateFlow<List<Int>>(emptyList())
    val availableYears: StateFlow<List<Int>> = _availableYears.asStateFlow()

    // Фильтр по году: null = все годы. Год храним отдельно от списка,
    // чтобы визуальный фильтр не терялся при обновлении записей.
    private val _selectedFilterYear = MutableStateFlow<Int?>(null)
    val selectedFilterYear: StateFlow<Int?> = _selectedFilterYear.asStateFlow()

    suspend fun refresh() {
        _availableYears.value = repository.getAvailableYears()
        applyFilter()
    }

    suspend fun setFilterYear(year: Int?) {
        _selectedFilterYear.value = year
        applyFilter()
    }

    private suspend fun applyFilter() {
        val year = _selectedFilterYear.value
        _records.value = if (year == null) {
            repository.getHistoryRecords()
        } else {
            repository.getHistoryRecordsByYear(year)
        }
        // Обновляем доступные годы после любого изменения данных.
        _availableYears.value = repository.getAvailableYears()
    }

    suspend fun saveRecord(monthIndex: Int, year: Int, monthName: String, totalClean: Double, cleanToPay: Double, advance: Double) {
        repository.saveHistoryRecord(monthIndex, year, monthName, totalClean, cleanToPay, advance)
        applyFilter()
    }

    suspend fun deleteRecord(monthIndex: Int, year: Int) {
        repository.deleteHistoryRecord(year, monthIndex)
        applyFilter()
    }

    suspend fun deleteAll() {
        repository.deleteAllHistory()
        _selectedFilterYear.value = null
        _records.value = emptyList()
        _availableYears.value = emptyList()
    }

    suspend fun exportToCsv(): File? = exporter.exportHistoryToCsv(repository.getHistoryEntities())

    suspend fun shareCsv(context: Context): Boolean = exporter.shareHistoryCsv(context, repository.getHistoryEntities())
}

/**
 * UI-состояние экрана расчёта зарплаты.
 * Вынесено на верхний уровень, чтобы чистая логика расчёта (SalaryCalculator)
 * могла использовать его без жёсткой привязки к ViewModel.
 */
data class SalaryUiState(
    val selectedMonthIndex: Int = LocalDate.now().monthValue - 1,
    val selectedYear: Int = LocalDate.now().year,
    val normHours: String = "",
    val prazdnHours: String = "0",
    val zaOtsutstvuushego: String = "",
    val kvartalka: String = "",
    val gazetaInput: String = "0",
    val pozhertvovanjaInput: String = "0",
    val subbotnikInput: String = "0",
    val mmDetiCountInput: String = "0",
    val childrenCountInput: String = "0",
    val stravitaInput: String = "0",
    val showResults: Boolean = false,
    val calculationResult: CalculationResultWithError? = null,
    val errorMessage: String? = null
)

/**
 * Результат расчёта зарплаты (без ошибки), либо флаг error.
 * Вынесен на верхний уровень для переиспользования в ViewModel, UI и тестах.
 */
data class CalculationResultWithError(
    val okladReal: Double = 0.0,
    val stazh: Double = 0.0,
    val vrednost: Double = 0.0,
    val nightHours: Double = 0.0,
    val nochPay: Double = 0.0,
    val prazdn: Double = 0.0,
    val prem: Double = 0.0,
    val mmDeti: Double = 0.0,
    val sumBeforePension: Double = 0.0,
    val pension: Double = 0.0,
    val dirty: Double = 0.0,
    val fszn: Double = 0.0,
    val prof: Double = 0.0,
    val childrenDeduction: Double = 0.0,
    val podohodnyBase: Double = 0.0,
    val podohodny: Double = 0.0,
    val avans: Double = 0.0,
    val totalClean: Double = 0.0,
    val cleanToPay: Double = 0.0,
    val error: String? = null
)

fun parseNonNegative(input: String): Double =
    input.replace(',', '.').replace(' ', '.')
        .toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0

fun parseMissedDays(input: String): Set<Int> =
    input.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.toSet()

fun displayInt(input: String): String =
    input.replace(',', '.').replace(' ', '.')
        .toDoubleOrNull()?.toInt()?.toString() ?: input

/** Форматирует коэффициент (0..1) как целые проценты для ввода в поле. */
fun percentInput(coef: Double): String =
    java.lang.String.format(java.util.Locale.US, "%.0f", coef * 100)

/**
 * Единая точка округления/форматирования денежных сумм (№20).
 * Раньше `String.format("...%.2f")` был разбросан по нескольким экранам
 * и PDF-экспортёру, что давало разный результат округления (и разный
 * разделитель). Теперь все деньги форматируются здесь через BigDecimal с
 * HALF_UP, чтобы начисление, история и экспорт совпадали до копейки.
 */
object MoneyFormatter {
    /** Округляет сумму до копеек (2 знака) по правилу HALF_UP. */
    fun round(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /** Форматирует сумму как «1234.56». Всегда 2 знака, без валюты. */
    fun format(value: Double): String =
        String.format(Locale.US, "%.2f", round(value))

    /** Форматирует сумму как «1234.56 руб». */
    fun formatRub(value: Double): String = "${format(value)} руб"

    /** Форматирует сумму как «1234.56 BYN». */
    fun formatByn(value: Double): String = "${format(value)} BYN"

    /** Форматирует сумму с 1 знаком (для компактного отображения). */
    fun format1(value: Double): String =
        String.format(Locale.US, "%.1f", round(value))
}
