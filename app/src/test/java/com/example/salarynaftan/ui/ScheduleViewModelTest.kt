package com.example.salarynaftan.ui

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleViewModelTest {
    @Test
    fun groupVacationDays_splitsRangeAcrossMonths() {
        val result = ScheduleViewModel.groupVacationDays(
            LocalDate.of(2026, 1, 30),
            LocalDate.of(2026, 2, 2)
        )

        assertEquals(setOf(30, 31), result[YearMonth.of(2026, 1)])
        assertEquals(setOf(1, 2), result[YearMonth.of(2026, 2)])
    }

    @Test
    fun groupVacationDays_acceptsDatesInReverseOrder() {
        val result = ScheduleViewModel.groupVacationDays(
            LocalDate.of(2026, 3, 3),
            LocalDate.of(2026, 3, 1)
        )

        assertEquals(setOf(1, 2, 3), result[YearMonth.of(2026, 3)])
    }
}