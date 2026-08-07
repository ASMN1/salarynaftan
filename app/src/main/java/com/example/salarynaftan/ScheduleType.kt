package com.example.salarynaftan

/**
 * Тип производственного графика завода.
 *
 * График №1 — 5 бригад, смены по 8 часов (утро/день/ночь), цикл 10 дней.
 * График №2 — 4 бригады, смены по 12 часов (утро/ночь), цикл 8 дней:
 *   «У У В В Н Н В В» для каждой бригады со своим сдвигом.
 *
 * От типа графика зависят: количество бригад, длительность смены, набор смен,
 * формула ночных часов и норма рабочего времени.
 */
enum class ScheduleType(
    val displayName: String,
    val brigadeCount: Int,
    /** Длительность одной смены в часах. */
    val shiftHours: Double,
    /** Дополнительные ночные часы, даваемые «дневной» сменой (в Графике №1). */
    val dayShiftNightBonusHours: Double
) {
    /**
     * График №1: 5 бригад, 8-часовые смены (Утро 08-16, День 16-00, Ночь 00-08).
     * Цикл 10 дней. Дневная смена даёт +2 ночных часа, как в исходной логике.
     */
    GRAPH_1(
        displayName = "График №1 · 5 бригад · 8 ч",
        brigadeCount = 5,
        shiftHours = 8.0,
        dayShiftNightBonusHours = 2.0
    ),

    /**
     * График №2: 4 бригады, 12-часовые смены (Утро 08-20, Ночь 20-08).
     * Цикл 8 дней: отдельные смены У/Н и выходные у каждой бригады.
     */
    GRAPH_2(
        displayName = "График №2 · 4 бригады · 12 ч",
        brigadeCount = 4,
        shiftHours = 12.0,
        dayShiftNightBonusHours = 0.0
    );

    /** Количество часов в сутках для проверки границ ввода. */
    fun maxNormHours(): Int = (31 * shiftHours * 2).toInt()

    /** Проверяет, что номер бригады валиден для этого графика. */
    fun isValidBrigade(brigade: Int): Boolean = brigade in 1..brigadeCount

    /** Список валидных номеров бригад для этого графика. */
    fun brigadeRange(): IntRange = 1..brigadeCount

    /** Строковый идентификатор для хранения в DataStore. */
    val storageName: String
        get() = when (this) {
            GRAPH_1 -> "graph_1"
            GRAPH_2 -> "graph_2"
        }

    companion object {
        /** Восстанавливает тип из строкового идентификатора хранилища. */
        fun fromStorageName(name: String?): ScheduleType =
            entries.firstOrNull { it.storageName == name } ?: GRAPH_1
    }
}
