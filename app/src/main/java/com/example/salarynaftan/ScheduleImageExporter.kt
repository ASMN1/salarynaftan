package com.example.salarynaftan

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale


object ScheduleImageExporter {


    fun createMonthImage(
        context: Context,
        brigade: Int,
        month: YearMonth
    ): File {
        val width = 1400
        val height = 1900

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ExportStyle.PAPER)

        val paint = Paint().apply { isAntiAlias = true }
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regular = Typeface.DEFAULT
        val margin = 70f
        val contentW = width - margin * 2

        // --- Шапка ---
        paint.color = ExportStyle.MUTED
        paint.typeface = bold
        paint.textSize = 24f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SALARYNAFTAN", margin, margin + 12f, paint)

        paint.color = ExportStyle.INK
        paint.textSize = 58f
        canvas.drawText("График смен", margin, margin + 82f, paint)

        paint.color = ExportStyle.MUTED
        paint.typeface = regular
        paint.textSize = 32f
        val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("ru"))
            .replaceFirstChar { it.uppercase() }
        canvas.drawText("Бригада №$brigade  ·  $monthName ${month.year}", margin, margin + 130f, paint)

        paint.color = ExportStyle.ACCENT
        canvas.drawRoundRect(margin, margin + 152f, width - margin, margin + 160f, 4f, 4f, paint)

        // --- Дни недели ---
        val weekDays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
        val cellW = contentW / 7f
        val gridTop = margin + 210f
        paint.typeface = bold
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        for (i in weekDays.indices) {
            paint.color = if (i == 6) ExportStyle.SUNDAY else ExportStyle.MUTED
            canvas.drawText(weekDays[i], margin + i * cellW + cellW / 2f, gridTop, paint)
        }

        // --- Сетка ---
        val gap = 10f
        val cellH = 150f
        val gridStartY = gridTop + 45f
        val today = java.time.LocalDate.now()

        var row = 0
        var workDays = 0

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val col = date.dayOfWeek.value - 1
            if (col == 0 && day != 1) row++

            val x0 = margin + col * cellW + gap / 2f
            val y0 = gridStartY + row * cellH + gap / 2f
            val x1 = x0 + cellW - gap
            val y1 = y0 + cellH - gap

            val shift = ShiftSchedule.shiftFor(date, brigade)

            paint.style = Paint.Style.FILL
            paint.color = shift.color.toArgb()
            canvas.drawRoundRect(x0, y0, x1, y1, 16f, 16f, paint)

            if (date == today) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = ExportStyle.ACCENT_DARK
                canvas.drawRoundRect(x0, y0, x1, y1, 16f, 16f, paint)
                paint.style = Paint.Style.FILL
            }

            val textColor = ExportStyle.textColorFor(shift)

            paint.textAlign = Paint.Align.LEFT
            paint.color = textColor
            paint.typeface = bold
            paint.textSize = 22f
            canvas.drawText(day.toString(), x0 + 12f, y0 + 28f, paint)

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 36f
            canvas.drawText(ExportStyle.shiftLetter(shift), (x0 + x1) / 2f, y0 + cellH * 0.68f, paint)

            if (shift != ShiftType.OFF) workDays++
        }

        val rows = row + 1
        val gridBottom = gridStartY + rows * cellH

        // --- Легенда ---
        paint.typeface = regular
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 22f
        var lx = margin
        val legendY = gridBottom + 55f
        for (shift in ExportStyle.LEGEND_ORDER) {
            paint.style = Paint.Style.FILL
            paint.color = shift.color.toArgb()
            canvas.drawRoundRect(lx, legendY - 24f, lx + 34f, legendY + 10f, 8f, 8f, paint)

            paint.color = ExportStyle.INK
            canvas.drawText("${ExportStyle.shiftLetter(shift)} — ${ExportStyle.shiftLabel(shift)}", lx + 46f, legendY, paint)
            lx += contentW / 4f
        }

        // --- Итоговая карточка ---
        val summaryY = legendY + 40f
        val summaryH = 130f
        paint.color = ExportStyle.ACCENT_DARK
        canvas.drawRoundRect(margin, summaryY, width - margin, summaryY + summaryH, 18f, 18f, paint)

        paint.color = ExportStyle.ACCENT_MUTED
        paint.typeface = regular
        paint.textSize = 19f
        canvas.drawText("РАБОЧИХ СМЕН", margin + 30f, summaryY + 42f, paint)
        canvas.drawText("ЧАСОВ ОТРАБОТАНО", margin + contentW / 2f + 30f, summaryY + 42f, paint)

        paint.color = ExportStyle.ACCENT
        paint.typeface = bold
        paint.textSize = 44f
        canvas.drawText(workDays.toString(), margin + 30f, summaryY + 95f, paint)
        canvas.drawText((workDays * 8).toString(), margin + contentW / 2f + 30f, summaryY + 95f, paint)

        // --- Подвал ---
        paint.color = ExportStyle.MUTED
        paint.typeface = regular
        paint.textSize = 18f
        canvas.drawText("Сформировано в приложении salarynaftan", margin, height - 40f, paint)

        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, "График_${brigade}_${month.monthValue}_${month.year}.png")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()

        return file
    }




    fun createYearImage(
        context: Context,
        brigade: Int,
        year: Int
    ): File {

        // Раньше тут собирался ОДИН битмап 1400×21600 в ARGB_8888 —
        // это ≈115 МБ памяти на одну картинку, реальный риск
        // OutOfMemoryError на части телефонов. Вместо этого каждый месяц
        // уменьшается вдвое перед склейкой, а итоговый холст рисуется в
        // RGB_565 (2 байта на пиксель вместо 4, альфа-канал тут не нужен —
        // фон и так непрозрачно-белый). Итоговый объём — около 15 МБ.
        val tileScale = 0.5f
        val tileWidth = (1400 * tileScale).toInt()
        val tileHeight = (1800 * tileScale).toInt()

        val width = tileWidth
        val height = 12 * tileHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var offset = 0

        for (monthNumber in 1..12) {
            val month = YearMonth.of(year, monthNumber)
            val temp = createMonthImage(context, brigade, month)

            val fullBitmap = BitmapFactory.decodeFile(temp.absolutePath)
            if (fullBitmap != null) {
                val scaledBitmap = Bitmap.createScaledBitmap(fullBitmap, tileWidth, tileHeight, true)
                fullBitmap.recycle()
                canvas.drawBitmap(scaledBitmap, 0f, offset.toFloat(), null)
                scaledBitmap.recycle()
            }

            temp.delete()

            // Временный файл месяца был нужен только чтобы прочитать его
            // обратно в Bitmap — больше не нужен, не оставляем мусор в кэше.
            temp.delete()

            offset += tileHeight
        }

        val dir2 = File(context.cacheDir, "exports")
        dir2.mkdirs()
        val file = File(dir2, "График_${brigade}_год_$year.png")

        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()

        return file
    }


}