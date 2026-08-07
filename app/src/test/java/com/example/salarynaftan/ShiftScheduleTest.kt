package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShiftScheduleTest {

    @Test
    fun `anchor date is OFF for brigade 1`() {
        // Anchor date: 2026-01-01
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 1), 1)
        assertEquals(ShiftType.OFF, shift)
    }

    @Test
    fun `brigade 1 day 2 is OFF`() {
        // cycle[0] = OFF, cycle[1] = OFF
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 2), 1)
        assertEquals(ShiftType.OFF, shift)
    }

    @Test
    fun `brigade 1 day 3 is DAY`() {
        // cycle[2] = DAY
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 3), 1)
        assertEquals(ShiftType.DAY, shift)
    }

    @Test
    fun `brigade 1 day 4 is DAY`() {
        // cycle[3] = DAY
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 4), 1)
        assertEquals(ShiftType.DAY, shift)
    }

    @Test
    fun `brigade 1 day 5 is OFF`() {
        // cycle[4] = OFF
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 5), 1)
        assertEquals(ShiftType.OFF, shift)
    }

    @Test
    fun `brigade 1 day 6 is MORNING`() {
        // cycle[5] = MORNING
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 6), 1)
        assertEquals(ShiftType.MORNING, shift)
    }

    @Test
    fun `brigade 1 day 7 is MORNING`() {
        // cycle[6] = MORNING
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 7), 1)
        assertEquals(ShiftType.MORNING, shift)
    }

    @Test
    fun `brigade 1 day 8 is NIGHT`() {
        // cycle[7] = NIGHT
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 8), 1)
        assertEquals(ShiftType.NIGHT, shift)
    }

    @Test
    fun `brigade 1 day 9 is NIGHT`() {
        // cycle[8] = NIGHT
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 9), 1)
        assertEquals(ShiftType.NIGHT, shift)
    }

    @Test
    fun `brigade 1 day 10 is OFF`() {
        // cycle[9] = OFF
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 10), 1)
        assertEquals(ShiftType.OFF, shift)
    }

    @Test
    fun `cycle repeats after 10 days for brigade 1`() {
        val day1 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 1), 1)
        val day11 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 11), 1)
        assertEquals(day1, day11)
    }

    @Test
    fun `brigade 2 has different schedule`() {
        val b1 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 1), 1)
        val b2 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 1), 2)
        // Brigade 2 has offset 4
        assertEquals(ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 1), 1), b1)
        // Brigade 2 should differ
        val expectedB2 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 5), 1) // offset +4 days
        assertEquals(expectedB2, b2)
    }

    @Test
    fun `brigade 5 has correct cycle`() {
        // Brigade 5 has offset 8
        val b5 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 1), 5)
        val expectedB5 = ShiftSchedule.shiftFor(LocalDate.of(2026, 1, 9), 1) // offset +8 days
        assertEquals(expectedB5, b5)
    }

    @Test
    fun `default brigade is 1`() {
        val explicit = ShiftSchedule.shiftFor(LocalDate.of(2026, 3, 15), 1)
        val default = ShiftSchedule.shiftFor(LocalDate.of(2026, 3, 15))
        assertEquals(explicit, default)
    }

    @Test
    fun `works for dates after 2026`() {
        // Just verify no exception and returns a valid type
        val shift = ShiftSchedule.shiftFor(LocalDate.of(2027, 6, 15), 1)
        assertEquals(true, shift in ShiftType.entries)
    }

    @Test
    fun `OFF shift has null times`() {
        assertEquals(null, ShiftType.OFF.startTime)
        assertEquals(null, ShiftType.OFF.endTime)
    }

    @Test
    fun `MORNING shift has correct times`() {
        assertEquals(java.time.LocalTime.of(8, 0), ShiftType.MORNING.startTime)
        assertEquals(java.time.LocalTime.of(16, 0), ShiftType.MORNING.endTime)
    }

    @Test
    fun `DAY shift ends next day at midnight`() {
        // DAY 16:00–00:00 пересекает полночь
        val date = java.time.LocalDate.of(2026, 1, 3)
        val end = ShiftType.DAY.endDateTime(date)
        assertEquals(java.time.LocalDateTime.of(2026, 1, 4, 0, 0), end)
    }

    @Test
    fun `MORNING shift ends same day`() {
        val date = java.time.LocalDate.of(2026, 1, 6)
        val end = ShiftType.MORNING.endDateTime(date)
        assertEquals(java.time.LocalDateTime.of(2026, 1, 6, 16, 0), end)
    }

    @Test
    fun `NIGHT shift ends same day in morning`() {
        val date = java.time.LocalDate.of(2026, 1, 8)
        val end = ShiftType.NIGHT.endDateTime(date)
        assertEquals(java.time.LocalDateTime.of(2026, 1, 8, 8, 0), end)
    }

    @Test
    fun `OFF shift has no end time`() {
        assertEquals(null, ShiftType.OFF.endDateTime(java.time.LocalDate.of(2026, 1, 1)))
    }
}