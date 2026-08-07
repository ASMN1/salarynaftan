package com.example.salarynaftan

import com.example.salarynaftan.data.SalaryHistoryEntity
import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.export.HistoryExporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryManagerTest {

    private val repo = mockk<SalaryRepository>()
    private val exporter = mockk<HistoryExporter>()

    private fun record(
        monthIndex: Int,
        year: Int,
        monthName: String = "Январь",
        totalClean: Double = 1000.0
    ) = SalaryHistoryRecord(monthIndex, year, monthName, totalClean, 900.0, 100.0)

    private fun entity(monthIndex: Int, year: Int, monthName: String = "Январь") =
        SalaryHistoryEntity(
            monthIndex = monthIndex,
            year = year,
            monthName = monthName,
            totalClean = 1000.0,
            cleanToPay = 900.0,
            advance = 100.0
        )

    @Test
    fun `refresh loads all records when no filter selected`() = runTest {
        val all = listOf(record(0, 2027), record(1, 2027), record(2, 2028))
        coEvery { repo.getAvailableYears() } returns listOf(2027, 2028)
        coEvery { repo.getHistoryRecords() } returns all

        val manager = HistoryManager(repo, exporter)
        manager.refresh()

        assertEquals(all, manager.records.value)
        assertEquals(listOf(2027, 2028), manager.availableYears.value)
        assertNull(manager.selectedFilterYear.value)
    }

    @Test
    fun `setFilterYear filters records by year`() = runTest {
        coEvery { repo.getAvailableYears() } returns listOf(2027, 2028)
        coEvery { repo.getHistoryRecords() } returns emptyList()
        coEvery { repo.getHistoryRecordsByYear(2027) } returns listOf(record(0, 2027), record(1, 2027))
        coEvery { repo.getHistoryRecordsByYear(2028) } returns listOf(record(2, 2028))

        val manager = HistoryManager(repo, exporter)
        manager.refresh()

        manager.setFilterYear(2027)
        assertEquals(listOf(record(0, 2027), record(1, 2027)), manager.records.value)
        assertEquals(2027, manager.selectedFilterYear.value)

        manager.setFilterYear(2028)
        assertEquals(listOf(record(2, 2028)), manager.records.value)
    }

    @Test
    fun `setFilterYear with null shows all records`() = runTest {
        coEvery { repo.getAvailableYears() } returns listOf(2027)
        coEvery { repo.getHistoryRecords() } returns emptyList()
        coEvery { repo.getHistoryRecordsByYear(2027) } returns listOf(record(0, 2027))

        val manager = HistoryManager(repo, exporter)
        manager.refresh()
        manager.setFilterYear(2027)

        coEvery { repo.getHistoryRecords() } returns listOf(record(0, 2027), record(2, 2028))
        manager.setFilterYear(null)

        assertNull(manager.selectedFilterYear.value)
        assertEquals(listOf(record(0, 2027), record(2, 2028)), manager.records.value)
    }

    @Test
    fun `saveRecord persists and applies filter`() = runTest {
        coEvery { repo.getAvailableYears() } returns listOf(2027)
        coEvery { repo.getHistoryRecords() } returns emptyList()
        coEvery { repo.saveHistoryRecord(any(), any(), any(), any(), any(), any()) } returns Unit

        val manager = HistoryManager(repo, exporter)
        manager.refresh()

        coEvery { repo.getHistoryRecords() } returns listOf(record(3, 2027))
        manager.saveRecord(3, 2027, "Апрель", 2000.0, 1800.0, 200.0)

        coVerify { repo.saveHistoryRecord(3, 2027, "Апрель", 2000.0, 1800.0, 200.0) }
        assertEquals(listOf(record(3, 2027)), manager.records.value)
    }

    @Test
    fun `deleteRecord removes and applies filter`() = runTest {
        coEvery { repo.getAvailableYears() } returns listOf(2027)
        coEvery { repo.getHistoryRecords() } returns listOf(record(0, 2027))
        coEvery { repo.getHistoryRecordsByYear(2027) } returns listOf(record(0, 2027))
        coEvery { repo.deleteHistoryRecord(any(), any()) } returns Unit

        val manager = HistoryManager(repo, exporter)
        manager.refresh()
        manager.setFilterYear(2027)

        coEvery { repo.getHistoryRecordsByYear(2027) } returns emptyList()
        manager.deleteRecord(0, 2027)

        coVerify { repo.deleteHistoryRecord(2027, 0) }
        assertTrue(manager.records.value.isEmpty())
    }

    @Test
    fun `deleteAll clears records and years`() = runTest {
        coEvery { repo.getAvailableYears() } returns listOf(2027, 2028)
        coEvery { repo.getHistoryRecords() } returns listOf(record(0, 2027))
        coEvery { repo.getHistoryRecordsByYear(2027) } returns listOf(record(0, 2027))
        coEvery { repo.deleteAllHistory() } returns Unit

        val manager = HistoryManager(repo, exporter)
        manager.refresh()
        manager.setFilterYear(2027)

        manager.deleteAll()

        coVerify { repo.deleteAllHistory() }
        assertTrue(manager.records.value.isEmpty())
        assertTrue(manager.availableYears.value.isEmpty())
        assertNull(manager.selectedFilterYear.value)
    }
}
