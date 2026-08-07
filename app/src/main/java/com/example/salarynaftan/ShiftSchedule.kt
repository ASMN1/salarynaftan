package com.example.salarynaftan

import java.time.LocalDate
import java.time.temporal.ChronoUnit


object ShiftSchedule {

    /**
     * Базовая дата цикла смен. По умолчанию — 2026-01-01, но её можно
     * переопределить из настроек (SettingsManager.saveAnchorDate / DataStore),
     * чтобы график можно было сдвинуть без пересборки (№12). Изменение
     * безопасно: формула одинакова для любой длины цикла и дат до/после базы.
     * @Volatile — читается из разных потоков (UI, AlarmScheduler, Receivers).
     */
    @Volatile
    var anchorDate: LocalDate = LocalDate.of(2026, 1, 1)


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

    // Валидация цикла при инициализации: пустой цикл привёл бы к делению
    // на ноль в расчёте индекса. Падаем сразу, а не в рантайме (№13).
    private val CYCLE_SIZE: Long = require(CYCLE.isNotEmpty()) {
        "Список смен (CYCLE) не должен быть пустым"
    }.let { CYCLE.size.toLong() }

    /**
     * Общедоступная длина цикла смен (дней). Используется, например, в
     * AlarmScheduler для детерминированного поиска следующего дня с нужным
     * типом смены: полный проход по циклу гарантированно находит её.
     */
    const val SHIFT_CYCLE_SIZE: Int = 10

    /** Диапазон допустимых номеров бригад (1..5). */
    const val MIN_BRIGADE = 1
    const val MAX_BRIGADE = 5

    // Смещения бригад фиксированы бизнес-правилом графика (см. getOffsetForBrigade).
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


    fun shiftFor(
        date: LocalDate,
        brigade: Int = 1
    ): ShiftType {
        // Валидация номера бригады (№5): некорректный номер молча давал бы
        // график 1-й бригады (её смещение 0). Падаем явно, чтобы ошибка
        // конфигурации всплыла сразу, а не дала «тихий» неверный график.
        require(brigade in MIN_BRIGADE..MAX_BRIGADE) {
            "Некорректный номер бригады: $brigade (допустимо $MIN_BRIGADE..$MAX_BRIGADE)"
        }

        val diff = ChronoUnit.DAYS.between(
            anchorDate,
            date
        )

        val offset = getOffsetForBrigade(brigade)

        // Единая формула: корректно обрабатывает и отрицательные разности
        // (даты раньше anchorDate), и любую длину цикла.
        val size = CYCLE_SIZE
        val idx = (((diff + offset) % size) + size) % size

        return CYCLE[idx.toInt()]
    }
}