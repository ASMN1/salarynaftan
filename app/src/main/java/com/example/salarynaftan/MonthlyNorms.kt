package com.example.salarynaftan

// Единый источник норм рабочего времени и умолчаний по месяцам.
// Раньше эти же числа были продублированы в двух местах —
// на экране "Зарплата" и на экране "График смен".

data class MonthData(
    val name: String,
    val norm: Double
)

object MonthlyNorms {
    // ===== СПИСОК МЕСЯЦЕВ В ИМЕНИТЕЛЬНОМ ПАДЕЖЕ (ДЛЯ КАЛЕНДАРЯ) =====
    val MONTH_NAMES_NOMINATIVE = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    val list: List<MonthData> = listOf(
        MonthData("Январь", 132.0),
        MonthData("Февраль", 140.0),
        MonthData("Март", 154.0),
        MonthData("Апрель", 145.0),
        MonthData("Май", 139.0),
        MonthData("Июнь", 154.0),
        MonthData("Июль", 153.0),
        MonthData("Август", 147.0),
        MonthData("Сентябрь", 154.0),
        MonthData("Октябрь", 154.0),
        MonthData("Ноябрь", 146.0),
        MonthData("Декабрь", 152.0)
    )

    // Норма часов для Графика №1 (35-часовая сокращённая рабочая неделя,
    // ст.113 ТК РБ, формула по ст.112,116 ТК РБ). Все данные 2026–2040 — из
    // производственного календаря РБ (пятидневка, колонка "Норма при 35-час.
    // неделе"). 2026 — официальное постановление Минтруда №95, 2027–2040 —
    // расчёт по методике ст.116,124 ТК РБ, проверенный на 2026 (совпадение).
    private val NORMS_BY_YEAR: Map<Int, List<Double>> = mapOf(
        2026 to listOf(132.0, 140.0, 154.0, 145.0, 139.0, 154.0, 153.0, 147.0, 154.0, 154.0, 146.0, 152.0),
        2027 to listOf(132.0, 140.0, 154.0, 153.0, 139.0, 154.0, 153.0, 154.0, 154.0, 147.0, 154.0, 159.0),
        2028 to listOf(139.0, 147.0, 153.0, 132.0, 146.0, 154.0, 140.0, 161.0, 147.0, 154.0, 146.0, 140.0),
        2029 to listOf(147.0, 140.0, 146.0, 138.0, 146.0, 147.0, 146.0, 161.0, 140.0, 161.0, 146.0, 138.0),
        2030 to listOf(140.0, 140.0, 139.0, 153.0, 138.0, 140.0, 153.0, 154.0, 147.0, 161.0, 139.0, 145.0),
        2031 to listOf(139.0, 140.0, 146.0, 145.0, 139.0, 147.0, 153.0, 147.0, 154.0, 161.0, 132.0, 152.0),
        2032 to listOf(132.0, 140.0, 154.0, 153.0, 139.0, 154.0, 153.0, 154.0, 154.0, 147.0, 154.0, 159.0),
        2033 to listOf(139.0, 140.0, 153.0, 147.0, 139.0, 154.0, 147.0, 161.0, 154.0, 147.0, 147.0, 154.0),
        2034 to listOf(146.0, 140.0, 153.0, 132.0, 146.0, 154.0, 140.0, 161.0, 147.0, 154.0, 146.0, 140.0),
        2035 to listOf(147.0, 140.0, 146.0, 146.0, 139.0, 147.0, 146.0, 161.0, 140.0, 161.0, 146.0, 138.0),
        2036 to listOf(140.0, 147.0, 146.0, 145.0, 139.0, 147.0, 153.0, 147.0, 154.0, 161.0, 132.0, 152.0),
        2037 to listOf(132.0, 140.0, 154.0, 145.0, 139.0, 154.0, 153.0, 147.0, 154.0, 154.0, 146.0, 152.0),
        2038 to listOf(132.0, 140.0, 154.0, 153.0, 139.0, 154.0, 153.0, 154.0, 154.0, 147.0, 154.0, 159.0),
        2039 to listOf(139.0, 140.0, 153.0, 139.0, 147.0, 154.0, 147.0, 161.0, 154.0, 147.0, 147.0, 154.0),
        2040 to listOf(146.0, 147.0, 146.0, 146.0, 138.0, 147.0, 146.0, 161.0, 140.0, 161.0, 146.0, 138.0)
    )

    // Норма часов для Графика №2 (40-часовая рабочая неделя, пятидневка).
    // Все данные 2026–2040 — 40-часовая неделя из производственного календаря
    // РБ (пятидневка). 2026 — официальное постановление Минтруда №95,
    // 2027–2040 — расчёт по методике ст.116,124 ТК РБ (праздники по Указу №157,
    // Радуница — по православной Пасхе), проверенный на 2026 (полное совпадение).
    private val NORMS_BY_YEAR_GRAPH2: Map<Int, List<Double>> = mapOf(
        2026 to listOf(151.0, 160.0, 176.0, 166.0, 159.0, 176.0, 175.0, 168.0, 176.0, 176.0, 167.0, 174.0),
        2027 to listOf(151.0, 160.0, 176.0, 175.0, 159.0, 176.0, 175.0, 176.0, 176.0, 168.0, 176.0, 182.0),
        2028 to listOf(159.0, 168.0, 175.0, 151.0, 167.0, 176.0, 160.0, 184.0, 168.0, 176.0, 167.0, 160.0),
        2029 to listOf(168.0, 160.0, 167.0, 158.0, 167.0, 168.0, 167.0, 184.0, 160.0, 184.0, 167.0, 158.0),
        2030 to listOf(160.0, 160.0, 159.0, 175.0, 158.0, 160.0, 175.0, 176.0, 168.0, 184.0, 159.0, 166.0),
        2031 to listOf(159.0, 160.0, 167.0, 166.0, 159.0, 168.0, 175.0, 168.0, 176.0, 184.0, 151.0, 174.0),
        2032 to listOf(151.0, 160.0, 176.0, 175.0, 159.0, 176.0, 175.0, 176.0, 176.0, 168.0, 176.0, 182.0),
        2033 to listOf(159.0, 160.0, 175.0, 168.0, 159.0, 176.0, 168.0, 184.0, 176.0, 168.0, 168.0, 176.0),
        2034 to listOf(167.0, 160.0, 175.0, 151.0, 167.0, 176.0, 160.0, 184.0, 168.0, 176.0, 167.0, 160.0),
        2035 to listOf(168.0, 160.0, 167.0, 167.0, 159.0, 168.0, 167.0, 184.0, 160.0, 184.0, 167.0, 158.0),
        2036 to listOf(160.0, 168.0, 167.0, 166.0, 159.0, 168.0, 175.0, 168.0, 176.0, 184.0, 151.0, 174.0),
        2037 to listOf(151.0, 160.0, 176.0, 166.0, 159.0, 176.0, 175.0, 168.0, 176.0, 176.0, 167.0, 174.0),
        2038 to listOf(151.0, 160.0, 176.0, 175.0, 159.0, 176.0, 175.0, 176.0, 176.0, 168.0, 176.0, 182.0),
        2039 to listOf(159.0, 160.0, 175.0, 159.0, 168.0, 176.0, 168.0, 184.0, 176.0, 168.0, 168.0, 176.0),
        2040 to listOf(167.0, 168.0, 167.0, 167.0, 158.0, 168.0, 167.0, 184.0, 160.0, 184.0, 167.0, 158.0)
    )

    /**
     * Норма часов для конкретного года и месяца (месяц 0-11).
     * @param scheduleType тип графика: для Графика №2 используются нормы
     *   12-часовых смен (таблица 2027–2040), для остальных — стандартные.
     * @return норма часов; для лет вне таблицы — значение по умолчанию.
     */
    fun norm(year: Int, monthIndex: Int, scheduleType: ScheduleType = ScheduleType.GRAPH_1): Double =
        if (scheduleType == ScheduleType.GRAPH_2) {
            NORMS_BY_YEAR_GRAPH2[year]?.getOrNull(monthIndex)
                ?: NORMS_BY_YEAR_GRAPH2[2026]!![monthIndex]
        } else {
            NORMS_BY_YEAR[year]?.getOrNull(monthIndex)
                ?: NORMS_BY_YEAR[2026]!![monthIndex]
        }

    // Годы, для которых известны нормы из таблицы
    fun supportedYears(): IntRange = 2026..2040
}

// Государственные (нерабочие) праздники Республики Беларусь.
// Единый источник, чтобы расчёт праздничных часов в календаре и расчётах
// зарплаты использовал один и тот же список.
object Holidays {

    /** Дата праздника + название для отображения на графике. */
    data class HolidayInfo(
        val date: java.time.MonthDay,
        val name: String
    )

    // Фиксированные даты государственных праздников (не зависят от года).
    val FIXED: List<HolidayInfo> = listOf(
        HolidayInfo(java.time.MonthDay.of(1, 1), "Новый год"),
        HolidayInfo(java.time.MonthDay.of(1, 7), "Рождество Христово (православн.)"),
        HolidayInfo(java.time.MonthDay.of(3, 8), "День женщин"),
        HolidayInfo(java.time.MonthDay.of(5, 1), "Праздник труда"),
        HolidayInfo(java.time.MonthDay.of(5, 9), "День Победы"),
        HolidayInfo(java.time.MonthDay.of(7, 3), "День Независимости"),
        HolidayInfo(java.time.MonthDay.of(11, 7), "День Октябрьской революции"),
        HolidayInfo(java.time.MonthDay.of(12, 25), "Рождество Христово (католич.)")
    )

    // Радуница — переходящий праздник (второй вторник после православной
    // Пасхи). Дата зависит от года, поэтому задана явной таблицей.
    private val RADUNITSA_BY_YEAR: Map<Int, java.time.MonthDay> = mapOf(
        2026 to java.time.MonthDay.of(4, 21),
        2027 to java.time.MonthDay.of(5, 11),
        2028 to java.time.MonthDay.of(4, 25),
        2029 to java.time.MonthDay.of(4, 17),
        2030 to java.time.MonthDay.of(5, 7),
        2031 to java.time.MonthDay.of(4, 28),
        2032 to java.time.MonthDay.of(5, 18),
        2033 to java.time.MonthDay.of(5, 10),
        2034 to java.time.MonthDay.of(4, 25),
        2035 to java.time.MonthDay.of(5, 15),
        2036 to java.time.MonthDay.of(4, 21),
        2037 to java.time.MonthDay.of(4, 13),
        2038 to java.time.MonthDay.of(5, 4),
        2039 to java.time.MonthDay.of(4, 27),
        2040 to java.time.MonthDay.of(4, 16)
    )

    /** Все нерабочие праздничные дни месяца (фиксированные + Радуница). */
    fun holidayDaysInMonth(year: Int, monthIndex: Int): Set<Int> {
        val month = monthIndex + 1
        return FIXED
            .filter { it.date.monthValue == month }
            .map { it.date.dayOfMonth }
            .toMutableSet()
            .apply {
                RADUNITSA_BY_YEAR[year]?.takeIf { it.monthValue == month }?.let { add(it.dayOfMonth) }
            }
    }

    /**
     * Праздники месяца с названиями (дата, название) для отображения списком.
     * Сортировка по дню месяца.
     */
    fun holidaysInMonth(year: Int, monthIndex: Int): List<Pair<Int, String>> {
        val month = monthIndex + 1
        val result = mutableListOf<Pair<Int, String>>()
        FIXED.filter { it.date.monthValue == month }
            .forEach { result.add(it.date.dayOfMonth to it.name) }
        RADUNITSA_BY_YEAR[year]
            ?.takeIf { it.monthValue == month }
            ?.let { result.add(it.dayOfMonth to "Радуница") }
        return result.sortedBy { it.first }
    }

    private val FIXED_SET: Set<java.time.MonthDay> = FIXED.map { it.date }.toSet()

    fun isHoliday(date: java.time.LocalDate): Boolean =
        java.time.MonthDay.from(date) in FIXED_SET ||
            RADUNITSA_BY_YEAR[date.year] == java.time.MonthDay.from(date)
}