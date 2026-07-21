package com.example.shiftalarm

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ShiftType(val label: String, val colorRes: Long) {
    MORNING("У", 0xFFFFE082), // Утро
    DAY("Д", 0xFFA5D6A7),     // День
    NIGHT("Н", 0xFF90CAF9),   // Ночь
    REST("В", 0xFFFFFFFF)     // Выходной
}

object ShiftSchedule {
    // Точка отсчета - 1 января 2026
    private val BASE_DATE = LocalDate.of(2026, 1, 1)

    // Эталонный 10-дневный цикл
    private val baseCycle = arrayOf(
        ShiftType.MORNING, ShiftType.MORNING, // 0, 1
        ShiftType.NIGHT, ShiftType.NIGHT,     // 2, 3
        ShiftType.REST, ShiftType.REST, ShiftType.REST, // 4, 5, 6
        ShiftType.DAY, ShiftType.DAY,         // 7, 8
        ShiftType.REST                      // 9
    )

    // Распределение сдвигов по бригадам (на основе присланных графиков)
    private fun getOffsetForBrigade(brigade: Int): Int {
        return when (brigade) {
            1 -> 5 // Оригинальный график (начинается с В, В, Д, Д)
            2 -> 0 // Скриншот 3 (начинается с У, У)
            3 -> 4 // Скриншот 1 (начинается с В, В, В, Д)
            4 -> 6 // Скриншот 2 (начинается с В, Д, Д)
            5 -> 7 // Скриншот 4 (начинается с Д, Д)
            else -> 5
        }
    }

    fun shiftFor(date: LocalDate, brigade: Int): ShiftType {
        val daysBetween = ChronoUnit.DAYS.between(BASE_DATE, date).toInt()
        val offset = getOffsetForBrigade(brigade)

        // Рассчитываем индекс цикла, поддерживая даты до 2026 года
        var cycleIndex = (daysBetween + offset) % 10
        if (cycleIndex < 0) {
            cycleIndex += 10
        }

        return baseCycle[cycleIndex]
    }
}