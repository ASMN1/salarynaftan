package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityFunctionsTest {

    // ===== parseNonNegative =====

    @Test
    fun `parseNonNegative returns 0 for empty string`() {
        assertEquals(0.0, parseNonNegative(""), 0.001)
    }

    @Test
    fun `parseNonNegative returns 0 for blank string`() {
        assertEquals(0.0, parseNonNegative("   "), 0.001)
    }

    @Test
    fun `parseNonNegative returns parsed value for valid number`() {
        assertEquals(42.5, parseNonNegative("42.5"), 0.001)
    }

    @Test
    fun `parseNonNegative handles comma as decimal separator`() {
        assertEquals(15.75, parseNonNegative("15,75"), 0.001)
    }

    @Test
    fun `parseNonNegative returns 0 for negative number`() {
        assertEquals(0.0, parseNonNegative("-10"), 0.001)
    }

    @Test
    fun `parseNonNegative returns 0 for non-numeric input`() {
        assertEquals(0.0, parseNonNegative("abc"), 0.001)
    }

    @Test
    fun `parseNonNegative returns 0 for mixed invalid input`() {
        assertEquals(0.0, parseNonNegative("12abc"), 0.001)
    }

    @Test
    fun `parseNonNegative handles zero`() {
        assertEquals(0.0, parseNonNegative("0"), 0.001)
    }

    @Test
    fun `parseNonNegative handles large numbers`() {
        assertEquals(999999.99, parseNonNegative("999999.99"), 0.001)
    }

    // ===== parseMissedDays =====

    @Test
    fun `parseMissedDays returns empty set for empty string`() {
        assertTrue(parseMissedDays("").isEmpty())
    }

    @Test
    fun `parseMissedDays parses single day`() {
        assertEquals(setOf(5), parseMissedDays("5"))
    }

    @Test
    fun `parseMissedDays parses multiple days`() {
        assertEquals(setOf(1, 3, 5), parseMissedDays("1,3,5"))
    }

    @Test
    fun `parseMissedDays filters out zero and negative`() {
        assertEquals(setOf(2), parseMissedDays("0,-1,2"))
    }

    @Test
    fun `parseMissedDays handles whitespace`() {
        assertEquals(setOf(10, 15), parseMissedDays("10, 15"))
    }

    @Test
    fun `parseMissedDays returns empty set for non-numeric`() {
        assertTrue(parseMissedDays("abc,def").isEmpty())
    }

    @Test
    fun `parseMissedDays deduplicates`() {
        assertEquals(setOf(7), parseMissedDays("7,7,7"))
    }

    // ===== displayInt =====

    @Test
    fun `displayInt returns integer string for decimal input`() {
        assertEquals("42", displayInt("42.7"))
    }

    @Test
    fun `displayInt returns original string for non-numeric`() {
        assertEquals("abc", displayInt("abc"))
    }

    @Test
    fun `displayInt handles comma decimal`() {
        assertEquals("15", displayInt("15,3"))
    }

    @Test
    fun `displayInt returns original for empty string`() {
        assertEquals("", displayInt(""))
    }

    @Test
    fun `displayInt rounds down for integer values`() {
        assertEquals("100", displayInt("100.0"))
    }
}