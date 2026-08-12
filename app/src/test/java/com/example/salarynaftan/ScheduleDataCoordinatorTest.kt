package com.example.salarynaftan

import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.ui.ScheduleDataCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleDataCoordinatorTest {
    private val repository = mockk<SalaryRepository>(relaxed = true)
    private val coordinator = ScheduleDataCoordinator(repository)

    @Test
    fun loadAnnotations_usesYearMonthCoordinates() = runTest {
        val month = YearMonth.of(2026, 3)
        coEvery { repository.getMissedDays(2026, 2) } returns setOf(1)
        coEvery { repository.getVacationDays(2026, 2) } returns setOf(5)

        val result = coordinator.loadAnnotations(month)

        assertEquals(setOf(1), result.missedDays)
        assertEquals(setOf(5), result.vacationDays)
        coVerify(exactly = 1) { repository.getMissedDays(2026, 2) }
        coVerify(exactly = 1) { repository.getVacationDays(2026, 2) }
    }

    @Test
    fun updateVacationDays_mergesWithPersistedValues() = runTest {
        coEvery { repository.getVacationDays(2026, 2) } returns setOf(5)

        coordinator.updateVacationDays(YearMonth.of(2026, 3), setOf(7))

        coVerify { repository.saveVacationDays(2026, 2, setOf(5, 7)) }
    }
}