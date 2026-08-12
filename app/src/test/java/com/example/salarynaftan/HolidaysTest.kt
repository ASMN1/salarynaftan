package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Тесты для Holidays — проверка фиксированных праздников и Радуницы.
 */
class HolidaysTest {

    @Test
    fun `fixed holidays are detected`() {
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 1, 1)))   // Новый год
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 1, 7)))   // Рождество православное
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 3, 8)))   // День женщин
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 5, 1)))   // Праздник труда
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 5, 9)))   // День Победы
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 7, 3)))   // День Независимости
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 11, 7)))  // День Октябрьской революции
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 12, 25))) // Рождество католическое
    }

    @Test
    fun `non-holiday dates are not holidays`() {
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 1, 15)))
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 6, 10)))
        assertFalse(Holidays.isHoliday(LocalDate.of(2027, 2, 28)))
    }

    @Test
    fun `radunitsa 2026 is April 21`() {
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 4, 21)))
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 4, 20)))
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 4, 22)))
    }

    @Test
    fun `radunitsa 2027 is May 11`() {
        assertTrue(Holidays.isHoliday(LocalDate.of(2027, 5, 11)))
    }

    @Test
    fun `radunitsa 2040 is April 16`() {
        assertTrue(Holidays.isHoliday(LocalDate.of(2040, 4, 16)))
    }

    @Test
    fun `holidayDaysInMonth returns correct days for January 2026`() {
        val days = Holidays.holidayDaysInMonth(2026, 0) // Январь
        assertTrue(1 in days)  // Новый год
        assertTrue(7 in days)  // Рождество
        assertEquals(2, days.size)
    }

    @Test
    fun `holidayDaysInMonth includes radunitsa in April 2026`() {
        val days = Holidays.holidayDaysInMonth(2026, 3) // Апрель
        assertTrue(21 in days) // Радуница
    }

    @Test
    fun `holidaysInMonth returns sorted list with names`() {
        val holidays = Holidays.holidaysInMonth(2026, 0) // Январь
        assertEquals(2, holidays.size)
        assertEquals(1, holidays[0].first)
        assertEquals("Новый год", holidays[0].second)
        assertEquals(7, holidays[1].first)
    }

    @Test
    fun `radunitsa exists for all years 2026-2040`() {
        for (year in 2026..2040) {
            val found = (1..12).any { month ->
                Holidays.holidayDaysInMonth(year, month - 1).isNotEmpty() &&
                    Holidays.holidaysInMonth(year, month - 1).any { it.second == "Радуница" }
            }
            assertTrue("Радуница не найдена для $year", found)
        }
    }
}