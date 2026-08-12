package com.example.salarynaftan

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit


object ShiftSchedule {

    /** Базовая дата цикла Графика №1 (5 бригад, 8 ч). */
    @Volatile
    var anchorDate: LocalDate = LocalDate.of(2026, 1, 1)

    /** Базовая дата цикла Графика №2 (4 бригады, 12 ч). 2026-08-08 (пн задания). */
    @Volatile
    var anchorDateGraph2: LocalDate = LocalDate.of(2026, 8, 8)

    /**
     * Безопасная установка базовой даты цикла (п.3.6): null/невалидная дата
     * отклоняется, чтобы не сломать детерминированный расчёт смен.
     * Название намеренно не setAnchorDate, т.к. var anchorDate генерирует
     * синтетический сеттер setAnchorDate(LocalDate), что приводит к
     * Platform declaration clash на JVM-уровне.
     */
    fun updateAnchorDate(date: LocalDate?) {
        if (date != null) anchorDate = date
    }

    // ===== График №1: 5 бригад, 8 ч (цикл 10 дней) =====

    private val CYCLE = listOf(
        ShiftType.OFF,
        ShiftType.OFF,
        ShiftType.DAY,
        ShiftType.DAY,
        ShiftType.OFF,
        ShiftType.MORNING,
        ShiftType.MORNING,
        ShiftType.NIGHT,
        ShiftType.NIGHT,
        ShiftType.OFF
    )

    private val CYCLE_SIZE: Long = require(CYCLE.isNotEmpty()) {
        "Список смен Графика №1 (CYCLE) не должен быть пустым"
    }.let { CYCLE.size.toLong() }

    /** Общедоступная длина цикла Графика №1 (дней). */
    const val SHIFT_CYCLE_SIZE: Int = 10

    /** Длина цикла для указанного типа графика (для детерминированного поиска дней). */
    fun cycleSizeFor(scheduleType: ScheduleType = ScheduleType.GRAPH_1): Int =
        when (scheduleType) {
            ScheduleType.GRAPH_1 -> CYCLE_SIZE.toInt()
            ScheduleType.GRAPH_2 -> CYCLE2_SIZE.toInt()
        }

    private fun getOffsetForBrigade(brigade: Int): Int {
        return when (brigade) {
            1 -> 0
            2 -> 4
            3 -> 6
            4 -> 2
            5 -> 8
            else -> 0
        }
    }

    // ===== График №2: 4 бригады, 12 ч (цикл 8 дней) =====
    // Мастер-цикл (бригада 4): У У В В Н Н В В (от сегодня 08.08).
    // Смещения смен для остальных бригад — циклический сдвиг того же цикла:
    //   Б1: В В Н Н В В У У  (сдвиг 2)
    //   Б2: Н Н В В У У В В  (сдвиг 4)
    //   Б3: В В У У В В Н Н  (сдвиг 6)
    //   Б4: У У В В Н Н В В  (сдвиг 0)
    private val CYCLE2 = listOf(
        ShiftType.MORNING, // У
        ShiftType.MORNING, // У
        ShiftType.OFF,     // В
        ShiftType.OFF,     // В
        ShiftType.NIGHT,   // Н
        ShiftType.NIGHT,   // Н
        ShiftType.OFF,     // В
        ShiftType.OFF      // В
    )

    val CYCLE2_SIZE: Long = CYCLE2.size.toLong()

    private fun getOffsetForBrigadeGraph2(brigade: Int): Int {
        return when (brigade) {
            1 -> 2
            2 -> 4
            3 -> 6
            4 -> 0
            else -> 0
        }
    }

    // ===== Общая логика =====

    /**
     * Смена для указанной даты и бригады активного графика.
     * [scheduleType] — тип графика, передаваемый вызывающим кодом явно.
     */
    fun shiftFor(
        date: LocalDate,
        brigade: Int = 1,
        scheduleType: ScheduleType = ScheduleType.GRAPH_1
    ): ShiftType = when (scheduleType) {
        ScheduleType.GRAPH_1 -> shiftForGraph1(date, brigade)
        ScheduleType.GRAPH_2 -> shiftForGraph2(date, brigade)
    }

    private fun shiftForGraph1(date: LocalDate, brigade: Int): ShiftType {
        val safeBrigade = brigade.coerceIn(1, ScheduleType.GRAPH_1.brigadeCount)
        val diff = ChronoUnit.DAYS.between(anchorDate, date)
        val offset = getOffsetForBrigade(safeBrigade)
        val idx = (((diff + offset) % CYCLE_SIZE) + CYCLE_SIZE) % CYCLE_SIZE
        return CYCLE[idx.toInt()]
    }

    private fun shiftForGraph2(date: LocalDate, brigade: Int): ShiftType {
        val safeBrigade = brigade.coerceIn(1, ScheduleType.GRAPH_2.brigadeCount)
        val diff = ChronoUnit.DAYS.between(anchorDateGraph2, date)
        val offset = getOffsetForBrigadeGraph2(safeBrigade)
        val idx = (((diff + offset) % CYCLE2_SIZE) + CYCLE2_SIZE) % CYCLE2_SIZE
        return CYCLE2[idx.toInt()]
    }

    // ===== Времена смен по графику =====

    /**
     * Время начала смены для активного графика. В Графике №2 смены 12-часовые:
     * Утро 08:00–20:00, Ночь 20:00–08:00 (в отличие от 8-часовых в Графике №1).
     */
    fun shiftStartTime(shift: ShiftType, scheduleType: ScheduleType): LocalTime? =
        when (scheduleType) {
            ScheduleType.GRAPH_1 -> shift.startTime
            ScheduleType.GRAPH_2 -> when (shift) {
                ShiftType.MORNING -> LocalTime.of(8, 0)
                ShiftType.NIGHT -> LocalTime.of(20, 0)
                else -> null
            }
        }

    /** Время конца смены для активного графика. */
    fun shiftEndTime(shift: ShiftType, scheduleType: ScheduleType): LocalTime? =
        when (scheduleType) {
            ScheduleType.GRAPH_1 -> shift.endTime
            ScheduleType.GRAPH_2 -> when (shift) {
                ShiftType.MORNING -> LocalTime.of(20, 0)
                ShiftType.NIGHT -> LocalTime.of(8, 0)
                else -> null
            }
        }

    /**
     * Момент окончания смены, назначенной на [date]. Ночь (20:00–08:00) в Графике №2
     * пересекает полночь и заканчивается на следующий день в 08:00.
     */
    fun shiftEndDateTime(
        date: LocalDate,
        shift: ShiftType,
        scheduleType: ScheduleType
    ): LocalDateTime? {
        val s = shiftStartTime(shift, scheduleType) ?: return null
        val e = shiftEndTime(shift, scheduleType) ?: return null
        val crossesMidnight = e.isBefore(s) || e == s
        return LocalDateTime.of(if (crossesMidnight) date.plusDays(1) else date, e)
    }
}
