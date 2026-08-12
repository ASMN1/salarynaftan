package com.example.salarynaftan

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetScheduleModelTest {
    @Test
    fun forMonth_returnsSixRowsAndKeepsInvalidCellsEmpty() {
        val today = LocalDate.of(2026, 2, 15)
        val cells = WidgetScheduleModel.forMonth(today, 1, ScheduleType.GRAPH_1)

        assertEquals(42, cells.size)
        assertEquals(28, cells.count { it != null })
        assertEquals(14, cells.count { it == null })
        assertNotNull(cells.first { it?.day == 15 })
        assertEquals(today, cells.first { it?.day == 15 }?.date)
    }

    @Test
    fun forMonth_marksPaydayOnPreviousWeekday() {
        val today = LocalDate.of(2026, 2, 1)
        val cells = WidgetScheduleModel.forMonth(today, 1, ScheduleType.GRAPH_1)

        assertEquals(1, cells.count { it?.isSalary == true })
        assertEquals(1, cells.count { it?.isAdvance == true })
        assertNull(cells.firstOrNull { it?.day == 0 })
    }
}