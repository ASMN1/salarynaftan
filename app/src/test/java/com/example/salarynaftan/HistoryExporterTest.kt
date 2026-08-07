package com.example.salarynaftan

import com.example.salarynaftan.data.SalaryHistoryEntity
import com.example.salarynaftan.export.HistoryExporter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import android.content.Context

class HistoryExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun contextWithCacheDir(): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        return context
    }

    private fun entity(
        monthIndex: Int,
        year: Int,
        monthName: String,
        totalClean: Double,
        cleanToPay: Double,
        advance: Double
    ) = SalaryHistoryEntity(
        monthIndex = monthIndex,
        year = year,
        monthName = monthName,
        totalClean = totalClean,
        cleanToPay = cleanToPay,
        advance = advance
    )

    @Test
    fun `export returns null for empty records`() {
        val exporter = HistoryExporter(contextWithCacheDir())
        assertNull(exporter.exportHistoryToCsv(emptyList()))
    }

    @Test
    fun `export writes csv file with header and rows`() {
        val exporter = HistoryExporter(contextWithCacheDir())
        val records = listOf(
            entity(0, 2027, "Январь", 1000.0, 900.0, 100.0),
            entity(1, 2027, "Февраль", 2000.0, 1800.0, 200.0)
        )

        val file = exporter.exportHistoryToCsv(records)

        assertNotNull(file)
        assertTrue("Файл должен существовать", file!!.exists())
        val content = file.readText()
        assertTrue("Должен быть заголовок", content.contains("Месяц;Год;Итого начислено;К выплате;Аванс"))
        assertTrue("Должна быть строка января", content.contains("Январь;2027;1000.00;900.00;100.00"))
        assertTrue("Должна быть строка февраля", content.contains("Февраль;2027;2000.00;1800.00;200.00"))
    }

    @Test
    fun `export writes file into exports subdirectory of cacheDir`() {
        val cacheDir = tempFolder.newFolder("cache")
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir

        val exporter = HistoryExporter(context)
        val file = exporter.exportHistoryToCsv(listOf(entity(0, 2027, "Январь", 1.0, 1.0, 1.0)))!!

        val parent = file.parentFile
        assertNotNull(parent)
        assertEquals("exports", parent!!.name)
        assertTrue(file.name.startsWith("history_export_"))
        assertTrue(file.name.endsWith(".csv"))
    }

    @Test
    fun `csv uses US locale decimal format`() {
        val exporter = HistoryExporter(contextWithCacheDir())
        val records = listOf(entity(0, 2027, "Январь", 1234.5, 1111.1, 123.45))

        val file = exporter.exportHistoryToCsv(records)!!

        val content = file.readText()
        // Десятичная точка, а не запятая
        assertTrue(content.contains("1234.50"))
        assertTrue(content.contains("123.45"))
    }
}
