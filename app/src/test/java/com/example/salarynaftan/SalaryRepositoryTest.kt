package com.example.salarynaftan

import android.content.Context
import com.example.salarynaftan.data.AppDatabase
import com.example.salarynaftan.data.HistoryDao
import com.example.salarynaftan.data.MonthSalaryDao
import com.example.salarynaftan.data.MonthSalaryEntity
import com.example.salarynaftan.data.SalaryHistoryEntity
import com.example.salarynaftan.data.SalaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SalaryRepositoryTest {

    private val context = mockk<Context>()
    private val db = mockk<AppDatabase>()
    private val monthDao = mockk<MonthSalaryDao>()
    private val historyDao = mockk<HistoryDao>()

    @Before
    fun setUp() {
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.monthSalaryDao() } returns monthDao
        every { db.historyDao() } returns historyDao
    }

    @After
    fun tearDown() {
        // no-op kept for clarity
    }

    private fun buildRepo() = SalaryRepository(context)

    // ===== Missed / vacation days =====

    @Test
    fun `getMissedDays parses comma-separated string into set`() = runTest {
        coEvery { monthDao.getMissedDays(2027, 0) } returns "1,3,5"
        val repo = buildRepo()
        assertEquals(setOf(1, 3, 5), repo.getMissedDays(2027, 0))
    }

    @Test
    fun `getMissedDays returns empty set when null`() = runTest {
        coEvery { monthDao.getMissedDays(2027, 0) } returns null
        val repo = buildRepo()
        assertTrue(repo.getMissedDays(2027, 0).isEmpty())
    }

    @Test
    fun `getVacationDays parses string into set`() = runTest {
        coEvery { monthDao.getVacationDays(2027, 6) } returns "10, 20"
        val repo = buildRepo()
        assertEquals(setOf(10, 20), repo.getVacationDays(2027, 6))
    }

    @Test
    fun `saveMissedDays writes sorted comma-joined string`() = runTest {
        coEvery { monthDao.updateMissedDays(any(), any(), any()) } returns Unit
        val repo = buildRepo()
        repo.saveMissedDays(2027, 0, setOf(5, 1, 3))
        coVerify { monthDao.updateMissedDays(2027, 0, "1,3,5") }
    }

    @Test
    fun `saveVacationDays writes sorted comma-joined string`() = runTest {
        coEvery { monthDao.updateVacationDays(any(), any(), any()) } returns Unit
        val repo = buildRepo()
        repo.saveVacationDays(2027, 6, setOf(9, 2))
        coVerify { monthDao.updateVacationDays(2027, 6, "2,9") }
    }

    // ===== History records mapping =====

    @Test
    fun `getHistoryRecords maps entities to records`() = runTest {
        val entities = listOf(
            SalaryHistoryEntity(monthIndex = 0, year = 2027, monthName = "Январь", totalClean = 1000.0, cleanToPay = 900.0, advance = 100.0),
            SalaryHistoryEntity(monthIndex = 1, year = 2027, monthName = "Февраль", totalClean = 2000.0, cleanToPay = 1800.0, advance = 200.0)
        )
        coEvery { historyDao.getAllRecords() } returns entities
        val repo = buildRepo()

        val records = repo.getHistoryRecords()
        assertEquals(2, records.size)
        assertEquals(SalaryHistoryRecord(0, 2027, "Январь", 1000.0, 900.0, 100.0), records[0])
        assertEquals(SalaryHistoryRecord(1, 2027, "Февраль", 2000.0, 1800.0, 200.0), records[1])
    }

    @Test
    fun `getHistoryRecordsByYear delegates to dao`() = runTest {
        coEvery { historyDao.getRecordsByYear(2028) } returns listOf(
            SalaryHistoryEntity(monthIndex = 5, year = 2028, monthName = "Июнь", totalClean = 3000.0, cleanToPay = 2700.0, advance = 300.0)
        )
        val repo = buildRepo()

        val records = repo.getHistoryRecordsByYear(2028)
        assertEquals(1, records.size)
        assertEquals(2028, records[0].year)
        assertEquals("Июнь", records[0].monthName)
    }

    @Test
    fun `getAvailableYears delegates to dao`() = runTest {
        coEvery { historyDao.getAvailableYears() } returns listOf(2028, 2027)
        val repo = buildRepo()
        assertEquals(listOf(2028, 2027), repo.getAvailableYears())
    }

    @Test
    fun `saveHistoryRecord builds entity and inserts`() = runTest {
        val slot = slot<SalaryHistoryEntity>()
        coEvery { historyDao.insertRecord(capture(slot)) } returns Unit
        val repo = buildRepo()

        repo.saveHistoryRecord(3, 2027, "Апрель", 1500.0, 1350.0, 150.0)

        val e = slot.captured
        assertEquals(3, e.monthIndex)
        assertEquals(2027, e.year)
        assertEquals("Апрель", e.monthName)
        assertEquals(1500.0, e.totalClean, 0.001)
        assertEquals(1350.0, e.cleanToPay, 0.001)
        assertEquals(150.0, e.advance, 0.001)
    }
}
