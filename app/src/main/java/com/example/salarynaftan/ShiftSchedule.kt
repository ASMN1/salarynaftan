package com.example.salarynaftan

import java.time.LocalDate
import java.time.temporal.ChronoUnit


object ShiftSchedule {

    private val ANCHOR_DATE: LocalDate =
        LocalDate.of(2026, 1, 1)


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

        val diff = ChronoUnit.DAYS.between(
            ANCHOR_DATE,
            date
        )

        val offset = getOffsetForBrigade(brigade)

        var idx =
            ((diff + offset) % CYCLE.size) % CYCLE.size

        if (idx < 0) {
            idx += CYCLE.size
        }

        return CYCLE[idx.toInt()]
    }
}