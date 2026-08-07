package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты единой точки округления/форматирования денежных сумм (№20).
 * Убеждаемся, что округление HALF_UP и формат «2 знака через точку»
 * стабильны для разных входных значений.
 */
class MoneyFormatterTest {

    @Test
    fun `round uses half up to two decimals`() {
        assertEquals(1234.57, MoneyFormatter.round(1234.565), 0.0)
        assertEquals(1234.56, MoneyFormatter.round(1234.564), 0.0)
        assertEquals(-5.68, MoneyFormatter.round(-5.675), 0.0)
        assertEquals(0.0, MoneyFormatter.round(0.001), 0.0)
    }

    @Test
    fun `format always produces two decimals with dot`() {
        assertEquals("1234.57", MoneyFormatter.format(1234.565))
        assertEquals("100.00", MoneyFormatter.format(100.0))
        assertEquals("0.00", MoneyFormatter.format(0.0))
        assertEquals("-5.68", MoneyFormatter.format(-5.675))
    }

    @Test
    fun `formatRub appends currency`() {
        assertEquals("100.00 руб", MoneyFormatter.formatRub(100.0))
        assertEquals("10.99 руб", MoneyFormatter.formatRub(10.993))
    }

    @Test
    fun `formatByn appends BYN`() {
        assertEquals("150.50 BYN", MoneyFormatter.formatByn(150.5))
    }

    @Test
    fun `format1 produces one decimal`() {
        assertEquals("100.0", MoneyFormatter.format1(100.04))
        assertEquals("100.1", MoneyFormatter.format1(100.06))
    }
}
