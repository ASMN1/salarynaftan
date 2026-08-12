package com.example.salarynaftan.export

import com.example.salarynaftan.ShiftSchedule
import com.example.salarynaftan.ShiftType
import com.example.salarynaftan.ScheduleType

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object SchedulePdfExporter {

    private val WEEK_DAYS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    private const val PAGE_W = ExportStyle.PDF_PAGE_W
    private const val PAGE_H = ExportStyle.PDF_PAGE_H
    private const val MARGIN = ExportStyle.PDF_MARGIN
    private const val CELL_H = ExportStyle.PDF_CELL_H

    // Рисует заголовок + цветную календарную сетку ОДНОГО месяца на уже
    // открытую страницу PDF. createMonthPdf() и createYearPdf() используют
    // одну и ту же функцию, так что год отличается от месяца только числом
    // страниц, а не содержимым.
    private fun drawMonthPage(canvas: Canvas, brigade: Int, month: YearMonth, scheduleType: ScheduleType) {
        val contentW = PAGE_W - MARGIN * 2
        canvas.drawColor(ExportStyle.PAPER)

        val paint = Paint().apply { isAntiAlias = true }
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regular = Typeface.DEFAULT

        // --- Шапка ---
        paint.color = ExportStyle.MUTED
        paint.typeface = bold
        paint.textSize = 8f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SALARYNAFTAN", MARGIN, MARGIN + 8f, paint)

        paint.color = ExportStyle.INK
        paint.textSize = 24f
        canvas.drawText("График смен", MARGIN, MARGIN + 38f, paint)

        paint.color = ExportStyle.MUTED
        paint.typeface = regular
        paint.textSize = 12f
        val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("ru"))
            .replaceFirstChar { it.uppercase() }
        canvas.drawText("Бригада №$brigade  ·  $monthName ${month.year}", MARGIN, MARGIN + 58f, paint)

        paint.color = ExportStyle.ACCENT
        canvas.drawRoundRect(MARGIN, MARGIN + 70f, PAGE_W - MARGIN, MARGIN + 74f, 2f, 2f, paint)

        // --- Дни недели ---
        val cellW = contentW / 7f
        val gridTop = MARGIN + 100f
        paint.typeface = bold
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        for (i in WEEK_DAYS.indices) {
            paint.color = if (i == 6) ExportStyle.SUNDAY else ExportStyle.MUTED
            canvas.drawText(WEEK_DAYS[i], MARGIN + i * cellW + cellW / 2f, gridTop, paint)
        }

        // --- Сетка ---
        val gap = 4f
        val cellH = CELL_H
        val gridStartY = gridTop + 18f
        val today = LocalDate.now()

        var row = 0
        var workDays = 0

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val col = date.dayOfWeek.value - 1
            if (col == 0 && day != 1) row++

            val x0 = MARGIN + col * cellW + gap / 2f
            val y0 = gridStartY + row * cellH + gap / 2f
            val x1 = x0 + cellW - gap
            val y1 = y0 + cellH - gap

            val shift = ShiftSchedule.shiftFor(date, brigade, scheduleType)

            paint.style = Paint.Style.FILL
            paint.color = shift.color.toArgb()
            canvas.drawRoundRect(x0, y0, x1, y1, 8f, 8f, paint)

            if (date == today) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = ExportStyle.ACCENT_DARK
                canvas.drawRoundRect(x0, y0, x1, y1, 8f, 8f, paint)
                paint.style = Paint.Style.FILL
            }

            val textColor = ExportStyle.textColorFor(shift)

            paint.textAlign = Paint.Align.LEFT
            paint.color = textColor
            paint.typeface = bold
            paint.textSize = 9f
            canvas.drawText(day.toString(), x0 + 5f, y0 + 13f, paint)

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 16f
            canvas.drawText(ExportStyle.shiftLetter(shift), (x0 + x1) / 2f, y0 + cellH * 0.62f, paint)

            if (shift != ShiftType.OFF) workDays++
        }

        val rows = row + 1
        val gridBottom = gridStartY + rows * cellH

        // Длительность смены зависит от активного графика (8ч у №1, 12ч у №2).
        val shiftHours = scheduleType.shiftHours

        // --- Легенда ---
        paint.typeface = regular
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9f
        var lx = MARGIN
        val legendY = gridBottom + 22f
        for (shift in ExportStyle.LEGEND_ORDER) {
            paint.style = Paint.Style.FILL
            paint.color = shift.color.toArgb()
            canvas.drawRoundRect(lx, legendY - 9f, lx + 10f, legendY + 1f, 2f, 2f, paint)

            paint.color = ExportStyle.INK
            canvas.drawText("${ExportStyle.shiftLetter(shift)} — ${ExportStyle.shiftLabel(shift)}", lx + 14f, legendY, paint)
            lx += contentW / 4f
        }

        // --- Итоговая карточка ---
        val summaryY = legendY + 16f
        val summaryH = 40f
        paint.color = ExportStyle.ACCENT_DARK
        canvas.drawRoundRect(MARGIN, summaryY, PAGE_W - MARGIN, summaryY + summaryH, 8f, 8f, paint)

        paint.color = ExportStyle.ACCENT_MUTED
        paint.textSize = 7f
        canvas.drawText("РАБОЧИХ СМЕН", MARGIN + 14f, summaryY + 14f, paint)
        canvas.drawText("ЧАСОВ ОТРАБОТАНО", MARGIN + contentW / 2f + 14f, summaryY + 14f, paint)

        paint.color = ExportStyle.ACCENT
        paint.typeface = bold
        paint.textSize = 18f
        canvas.drawText(workDays.toString(), MARGIN + 14f, summaryY + 33f, paint)
        canvas.drawText((workDays * shiftHours).toString(), MARGIN + contentW / 2f + 14f, summaryY + 33f, paint)

        // --- Подвал ---
        paint.color = ExportStyle.MUTED
        paint.typeface = regular
        paint.textSize = 7f
        canvas.drawText("Сформировано в приложении salarynaftan", MARGIN, PAGE_H - 22f, paint)
    }

    suspend fun createMonthPdf(
        context: Context,
        brigade: Int,
        month: YearMonth,
        scheduleType: ScheduleType
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create()
        val page = document.startPage(pageInfo)

        drawMonthPage(page.canvas, brigade, month, scheduleType)

        document.finishPage(page)

        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, "График_бригада_${brigade}_${month.monthValue}_${month.year}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }

    suspend fun createYearPdf(
        context: Context,
        brigade: Int,
        year: Int,
        scheduleType: ScheduleType
    ): File {
        val document = PdfDocument()

        for (monthNumber in 1..12) {
            val month = YearMonth.of(year, monthNumber)
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), monthNumber).create()
            val page = document.startPage(pageInfo)

            drawMonthPage(page.canvas, brigade, month, scheduleType)

            document.finishPage(page)
        }

        val dir2 = File(context.cacheDir, "exports")
        dir2.mkdirs()
        val file = File(dir2, "График_бригада_${brigade}_год_$year.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }
}
