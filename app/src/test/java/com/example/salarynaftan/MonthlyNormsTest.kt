package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyNormsTest {

    @Test
    fun `list has 12 months`() {
        assertEquals(12, MonthlyNorms.list.size)
    }

    @Test
    fun `MONTH_NAMES_NOMINATIVE has 12 entries`() {
        assertEquals(12, MonthlyNorms.MONTH_NAMES_NOMINATIVE.size)
    }

    @Test
    fun `first month is January`() {
        assertEquals("Январь", MonthlyNorms.list[0].name)
    }

    @Test
    fun `last month is December`() {
        assertEquals("Декабрь", MonthlyNorms.list[11].name)
    }

    @Test
    fun `all norms are positive`() {
        MonthlyNorms.list.forEach { month ->
            org.junit.Assert.assertTrue("Norm for ${month.name} should be > 0", month.norm > 0)
        }
    }

    @Test
    fun `month names match nominative list`() {
        MonthlyNorms.list.forEachIndexed { index, month ->
            assertEquals(MonthlyNorms.MONTH_NAMES_NOMINATIVE[index], month.name)
        }
    }

    @Test
    fun `year norm for 2027 January is 132`() {
        assertEquals(132.0, MonthlyNorms.norm(2027, 0), 0.001)
    }

    @Test
    fun `year norm for 2027 December is 159`() {
        assertEquals(159.0, MonthlyNorms.norm(2027, 11), 0.001)
    }

    @Test
    fun `year norm for 2027 April matches spreadsheet`() {
        assertEquals(153.0, MonthlyNorms.norm(2027, 3), 0.001)
    }

    @Test
    fun `year norm for 2027 August matches spreadsheet`() {
        assertEquals(154.0, MonthlyNorms.norm(2027, 7), 0.001)
    }

    @Test
    fun `year norm for 2035 September matches spreadsheet`() {
        assertEquals(140.0, MonthlyNorms.norm(2035, 8), 0.001)
    }

    @Test
    fun `supported years covers 2027 to 2035`() {
        assertEquals(2027, MonthlyNorms.supportedYears().first)
        assertEquals(2035, MonthlyNorms.supportedYears().last)
    }

    @Test
    fun `unsupported year falls back to default norm`() {
        assertEquals(MonthlyNorms.list[0].norm, MonthlyNorms.norm(2026, 0), 0.001)
    }
}