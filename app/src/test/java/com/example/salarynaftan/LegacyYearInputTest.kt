package com.example.salarynaftan

import com.example.salarynaftan.ui.LegacyYearInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyYearInputTest {
    @Test fun acceptsTrimmedPositiveYear() = assertEquals(2026, LegacyYearInput.parse(" 2026 "))
    @Test fun rejectsUnknownAndImpossibleYears() {
        assertNull(LegacyYearInput.parse("0"))
        assertNull(LegacyYearInput.parse("-1"))
        assertNull(LegacyYearInput.parse("10000"))
        assertNull(LegacyYearInput.parse("year"))
    }
}