package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSyncResultTest {
    @Test fun negativeCountIsFailure() {
        assertEquals(CalendarSyncResult.Failed, CalendarSyncResult.fromCount(-1))
    }

    @Test fun zeroAndPositiveCountsAreSuccess() {
        assertEquals(CalendarSyncResult.Success(0), CalendarSyncResult.fromCount(0))
        assertEquals(CalendarSyncResult.Success(4), CalendarSyncResult.fromCount(4))
    }
}