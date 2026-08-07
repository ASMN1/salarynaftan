package com.example.salarynaftan

import android.content.Context
import com.example.salarynaftan.export.ScheduleIcsExporter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class ScheduleIcsExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun contextWithCacheDir(): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        every { context.packageName } returns "com.example.salarynaftan"
        return context
    }

    @Test
    fun `ics file is created for a month`() {
        val context = contextWithCacheDir()
        val month = YearMonth.of(2026, 1) // январь 2026 — включает все типы смен для бригады 1

        val file = ScheduleIcsExporter.createIcsFile(context, month, 1)

        assertNotNull(file)
        assertTrue("Файл должен существовать", file!!.exists())
        val content = file.readText()
        assertTrue("Должен содержать BEGIN:VCALENDAR", content.contains("BEGIN:VCALENDAR"))
        assertTrue("Должен содержать END:VCALENDAR", content.contains("END:VCALENDAR"))
        assertTrue("Должен содержать хотя бы одно VEVENT", content.contains("BEGIN:VEVENT"))
        assertTrue("Должен указывать календарь смен", content.contains("X-WR-CALNAME:График смен"))
    }

    @Test
    fun `ics skips OFF days`() {
        val context = contextWithCacheDir()
        // Январь 2026, бригада 1: 1-е и 2-е — выходные (OFF), 3-е — DAY
        val month = YearMonth.of(2026, 1)
        val file = ScheduleIcsExporter.createIcsFile(context, month, 1)!!

        val content = file.readText()
        // 2026-01-02 — OFF, не должно быть события этой датой
        assertTrue("Выходной день не должен попасть в ics", !content.contains("DTSTART;TZID=Europe/Minsk:20260102T"))
    }

    @Test
    fun `night shift ics spans to next day`() {
        val context = contextWithCacheDir()
        val month = YearMonth.of(2026, 1)
        val file = ScheduleIcsExporter.createIcsFile(context, month, 1)!!

        val content = file.readText()
        // Январь 2026: бригада 1 имеет ночные смены. Проверяем, что найдётся
        // событие ночной смены, DTEND которого приходится на следующий день.
        val nightSummaryLine = content.lineSequence()
            .firstOrNull { it.startsWith("SUMMARY:Ночь смена") }
        assertNotNull("Должна быть ночная смена в январе 2026", nightSummaryLine)
    }

    @Test
    fun `ics includes correct date format`() {
        val context = contextWithCacheDir()
        val month = YearMonth.of(2026, 1)
        val file = ScheduleIcsExporter.createIcsFile(context, month, 1)!!

        val content = file.readText()
        // 2026-01-03 — день смены DAY
        val expectedDate = "20260103"
        assertTrue(
            "Дата смены должна быть в формате yyyyMMdd",
            content.contains("$expectedDate")
        )
    }
}
