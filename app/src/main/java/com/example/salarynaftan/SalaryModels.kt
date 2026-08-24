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
        // applyFilter уже обновляет и записи, и доступные годы.
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
    val zaOtsutstvuushego: String = "",
    val kvartalka: String = "",
    val gazetaInput: String = "0",
    val pozhertvovanjaInput: String = "0",
    val subbotnikInput: String = "0",
    val mmDetiCountInput: String = "0",
    val childrenCountInput: String = "0",
    val stravitaInput: String = "0",
    val inyeVyplatyInput: String = "0",
    val inyeUderzhanijaInput: String = "0",
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
    val profMasterstvo: Double = 0.0,
    val intensyvnost: Double = 0.0,
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
    input.replace(',', '.').replace(" ", "") // «1 000» -> «1000», а не «1.000»
        .toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0

/** Элемент «иных выплат/удержаний»: название + сумма. */
data class ExtraItem(val name: String, val amount: Double)

/**
 * Разбирает строку доплат в список позиций «название:сумма», разделённых `;`.
 * Пример: "Премия:100;Компенсация:50" → [("Премия",100),("Компенсация",50)].
 * Если у позиции нет названия — она превращается в ("", сумма).
 */
fun parseExtraItems(raw: String): List<ExtraItem> {
    if (raw.isBlank()) return emptyList()
    return raw.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { seg ->
            val idx = seg.indexOf(':')
            if (idx > 0) {
                ExtraItem(seg.substring(0, idx).trim(), parseNonNegative(seg.substring(idx + 1)))
            } else {
                ExtraItem("", parseNonNegative(seg))
            }
        }
}

/** Общая сумма всех позиций «иных» доплат/удержаний (для расчёта). */
fun sumExtraItems(raw: String): Double = parseExtraItems(raw).sumOf { it.amount }

/**
 * Собирает строку-хранилище из списка позиций. Пустые строки (без названия
 * и суммы) пропускаются, чтобы не плодить «:0».
 */
fun buildExtraRaw(items: List<ExtraItem>): String =
    items
        .filter { it.name.isNotBlank() || it.amount > 0 }
        .joinToString(";") { "${it.name.trim()}:${MoneyFormatter.format(it.amount)}" }

/**
 * Адаптер из UI-состояния в входные данные расчёта. Размещён рядом с
 * SalaryUiState (тот же файл), чтобы чистая логика SalaryCalculator не
 * зависела от UI-структуры.
 */
fun monthInputFrom(state: SalaryUiState): SalaryCalculator.MonthInput = SalaryCalculator.MonthInput(
    normHours = parseNonNegative(state.normHours),
    zaOtsutstvuushego = parseNonNegative(state.zaOtsutstvuushego),
    kvartalka = parseNonNegative(state.kvartalka),
    gazetaInput = parseNonNegative(state.gazetaInput),
    pozhertvovanjaInput = parseNonNegative(state.pozhertvovanjaInput),
    subbotnikInput = parseNonNegative(state.subbotnikInput),
    mmDetiCount = parseNonNegative(state.mmDetiCountInput),
    childrenCount = parseNonNegative(state.childrenCountInput),
    stravitaInput = parseNonNegative(state.stravitaInput),
    inyeVyplaty = sumExtraItems(state.inyeVyplatyInput),
    inyeUderzhanija = sumExtraItems(state.inyeUderzhanijaInput)
)

fun parseMissedDays(input: String): Set<Int> =
    input.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.toSet()

fun parseMissedDays(input: String, year: Int, monthIndex: Int): Set<Int> {
    val maxDay = java.time.YearMonth.of(year, monthIndex + 1).lengthOfMonth()
    return parseMissedDays(input).filter { it in 1..maxDay }.toSet()
}

fun displayInt(input: String): String =
    input.replace(',', '.').replace(" ", "")
        .toDoubleOrNull()?.toInt()?.toString() ?: input

/** Форматирует коэффициент (0..1) как целые проценты для ввода в поле. */
fun percentInput(coef: Double): String =
    java.lang.String.format(java.util.Locale.US, "%.0f", coef * 100)

/** Вариант выбора (подпись + коэффициент) для селекторов стажа и класса вредности. */
data class CoefOption(val label: String, val coef: Double)

/** Таблица «коэффициент вредности» (надбавка за стаж) из Зарплата6.xlsx (R/S). */
val STAZH_COEF_OPTIONS = listOf(
    CoefOption("до 1 года", 0.10),
    CoefOption("1–5 лет", 0.20),
    CoefOption("5–10 лет", 0.25),
    CoefOption("10–15 лет", 0.35),
    CoefOption("15–30 лет", 0.45),
    CoefOption("более 30 лет", 0.50),
)

/** Классы вредности (КОЭФ.КЛАССА / 100 = ставка вредности за час). */
val harmClassOptions = listOf(
    CoefOption("1 класс", 0.20),
    CoefOption("2 класс", 0.14),
    CoefOption("3 класс", 0.10),
)

/** Разряды и их базовые ставки (из Зарплата6.xlsx, столбцы N/P). */
val RANK_BASE_RATE_OPTIONS = listOf(
    CoefOption("7 разряд", 613.55),
    CoefOption("6 разряд", 574.26),
    CoefOption("5 разряд", 522.88),
    CoefOption("4 разряд", 474.52),
    CoefOption("1 разряд", 302.24),
)

/** Подпись варианта по значению коэффициента (для стажа/класса вредности). */
fun coefOptionLabel(options: List<CoefOption>, coef: Double): String {
    // Сравнение с допуском: сохранённое значение могло прийти из Float с потерей
    // точности (0.14 → 0.14000000000000001), поэтому точное == не подходит.
    val rounded = Math.round(coef * 1000.0) / 1000.0
    val found = options.find { Math.round(it.coef * 1000.0) / 1000.0 == rounded }
    return found?.label ?: percentInput(coef)
}

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
        if (value.isFinite()) BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble() else 0.0

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
