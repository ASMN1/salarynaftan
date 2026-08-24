package com.example.salarynaftan.export

import com.example.salarynaftan.CalculationResultWithError
import com.example.salarynaftan.MoneyFormatter
import com.example.salarynaftan.SalaryUiState
import com.example.salarynaftan.parseNonNegative
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SalaryPdfExporter {

    private const val PAGE_W = ExportStyle.PDF_PAGE_W
    private const val PAGE_H = ExportStyle.PDF_PAGE_H
    private const val MARGIN = ExportStyle.PDF_MARGIN

    suspend fun createPdf(
        context: Context,
        monthName: String,
        state: SalaryUiState,
        result: CalculationResultWithError,
        stazhPercent: Int,
        premiumPercent: Int,
        pensionPercent: Int
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create()
        val page = document.startPage(pageInfo)

        drawSalaryPage(page.canvas, monthName, state, result, stazhPercent, premiumPercent, pensionPercent)

        document.finishPage(page)

        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, "Расчёт_зарплаты_${monthName.replace(" ", "_")}.pdf")
        // Запись с повторами: разовый сбой файловой системы (занятый файл,
        // медленный кэш) не должен ронять экспорт (п.6.1).
        ExportRetry.withRetry(operationName = "PDF расчёта зарплаты") {
            FileOutputStream(file).use { out -> document.writeTo(out) }
        }
        document.close()

        return file
    }

    private fun drawSalaryPage(
        canvas: Canvas,
        monthName: String,
        state: SalaryUiState,
        result: CalculationResultWithError,
        stazhPercent: Int,
        premiumPercent: Int,
        pensionPercent: Int
    ) {
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
        paint.textSize = 22f
        canvas.drawText("Расчёт зарплаты", MARGIN, MARGIN + 34f, paint)

        paint.color = ExportStyle.MUTED
        paint.typeface = regular
        paint.textSize = 12f
        canvas.drawText("ОАО «Нафтан»  ·  $monthName", MARGIN, MARGIN + 52f, paint)

        // Акцентная полоска
        paint.color = ExportStyle.ACCENT
        canvas.drawRoundRect(MARGIN, MARGIN + 64f, PAGE_W - MARGIN, MARGIN + 67f, 2f, 2f, paint)

        // --- Таблица начислений ---
        var y = MARGIN + 90f
        val rowH = 22f
        val labelX = MARGIN
        val valueX = PAGE_W - MARGIN

        paint.typeface = bold
        paint.textSize = 12f
        paint.color = ExportStyle.ACCENT_DARK
        canvas.drawText("НАЧИСЛЕНИЯ", labelX, y, paint)
        y += 6f
        paint.color = ExportStyle.ACCENT
        canvas.drawRoundRect(labelX, y, PAGE_W - MARGIN, y + 2f, 1f, 1f, paint)
        y += rowH

        val accretions = listOf(
            "Оклад" to result.okladReal,
            "Стаж (${formatPercent(stazhPercent)})" to result.stazh,
            "Вредность" to result.vrednost,
            "Ночные часы (${result.nightHours.toInt()} ч)" to result.nochPay,
            "Праздничные" to result.prazdn,
            "Премия (${formatPercent(premiumPercent)})" to result.prem,
            "За отсутствующего" to parseNonNegative(state.zaOtsutstvuushego),
            "Квартальная" to parseNonNegative(state.kvartalka),
            "ММ «Дети» (${parseNonNegative(state.mmDetiCountInput).toInt()} × 45.00)" to result.mmDeti,
        )

        paint.typeface = regular
        paint.textSize = 10f
        for ((label, value) in accretions) {
            if (value > 0.0) {
                y = drawRow(canvas, paint, labelX, valueX, y, label, value, rowH)
            }
        }

        // Подитог
        y += 4f
        paint.color = ExportStyle.ACCENT_DARK
        paint.typeface = bold
        paint.textSize = 11f
        canvas.drawText("Итого до вычетов:", labelX, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(fmt(result.sumBeforePension), valueX, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += 6f
        paint.color = ExportStyle.ACCENT
        canvas.drawRoundRect(labelX, y, PAGE_W - MARGIN, y + 2f, 1f, 1f, paint)
        y += rowH

        // --- Вычеты ---
        paint.typeface = bold
        paint.textSize = 12f
        paint.color = ExportStyle.SUNDAY
        canvas.drawText("ВЫЧЕТЫ", labelX, y, paint)
        y += 6f
        paint.color = ExportStyle.SUNDAY
        paint.alpha = 100
        canvas.drawRoundRect(labelX, y, PAGE_W - MARGIN, y + 2f, 1f, 1f, paint)
        paint.alpha = 255
        y += rowH

        val deductions = listOf(
            "Пенсионный (${pensionPercent}%)" to result.pension,
            "ФСЗН (1%)" to result.fszn,
            "Профсоюз (1%)" to result.prof,
            "Подоходный налог (13%)" to result.podohodny,
            "Вычет на детей (${parseNonNegative(state.childrenCountInput).toInt()} × 63.00)" to result.childrenDeduction,
            "Газета" to parseNonNegative(state.gazetaInput),
            "Пожертвования" to parseNonNegative(state.pozhertvovanjaInput),
            "Субботник" to parseNonNegative(state.subbotnikInput),
            "Стравита" to parseNonNegative(state.stravitaInput),
        )

        paint.typeface = regular
        paint.textSize = 10f
        for ((label, value) in deductions) {
            if (value > 0.0) {
                y = drawRow(canvas, paint, labelX, valueX, y, label, value, rowH)
            }
        }

        // --- Итоговые блоки ---
        y += 8f
        val cardH = 36f
        val gap = 8f
        val halfW = (contentW - gap) / 2f

        // Всего начислено
        paint.color = ExportStyle.ACCENT_DARK
        canvas.drawRoundRect(MARGIN, y, MARGIN + halfW, y + cardH, 8f, 8f, paint)
        paint.color = ExportStyle.ACCENT_MUTED
        paint.typeface = regular
        paint.textSize = 7f
        canvas.drawText("ИТОГО НАЧИСЛЕНО", MARGIN + 10f, y + 14f, paint)
        paint.color = ExportStyle.ACCENT
        paint.typeface = bold
        paint.textSize = 16f
        canvas.drawText(fmt(result.dirty), MARGIN + 10f, y + 30f, paint)

        // К выплате
        val card2X = MARGIN + halfW + gap
        paint.color = ExportStyle.ACCENT
        canvas.drawRoundRect(card2X, y, PAGE_W - MARGIN, y + cardH, 8f, 8f, paint)
        paint.color = ExportStyle.PAPER
        paint.typeface = regular
        paint.textSize = 7f
        canvas.drawText("К ВЫПЛАТЕ", card2X + 10f, y + 14f, paint)
        paint.typeface = bold
        paint.textSize = 16f
        canvas.drawText(fmt(result.cleanToPay), card2X + 10f, y + 30f, paint)

        y += cardH + gap

        // Аванс
        paint.color = ExportStyle.ACCENT_DARK
        canvas.drawRoundRect(MARGIN, y, MARGIN + halfW, y + cardH, 8f, 8f, paint)
        paint.color = ExportStyle.ACCENT_MUTED
        paint.typeface = regular
        paint.textSize = 7f
        canvas.drawText("АВАНС", MARGIN + 10f, y + 14f, paint)
        paint.color = ExportStyle.ACCENT
        paint.typeface = bold
        paint.textSize = 16f
        canvas.drawText(fmt(result.avans), MARGIN + 10f, y + 30f, paint)

        // --- Входные данные ---
        y += cardH + 20f
        paint.color = ExportStyle.MUTED
        paint.typeface = bold
        paint.textSize = 9f
        canvas.drawText("ИСХОДНЫЕ ДАННЫЕ", labelX, y, paint)
        y += 14f

        val inputs = listOf(
            "Норма часов" to "${parseNonNegative(state.normHours).toInt()} ч",
            "Ночные часы" to "${result.nightHours.toInt()} ч",
            "Праздн. часы" to "${result.prazdn.toInt()} ч",
            "Детей (вычет)" to "${parseNonNegative(state.childrenCountInput).toInt()}",
        )

        paint.typeface = regular
        paint.textSize = 8f
        for ((label, value) in inputs) {
            paint.color = ExportStyle.MUTED
            canvas.drawText(label, labelX, y, paint)
            paint.color = ExportStyle.INK
            paint.typeface = bold
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(value, valueX, y, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = regular
            y += 14f
        }

        // --- Подвал ---
        paint.color = ExportStyle.MUTED
        paint.textSize = 7f
        canvas.drawText("Сформировано в приложении salarynaftan", MARGIN, PAGE_H - 22f, paint)
    }

    private fun drawRow(
        canvas: Canvas, paint: Paint,
        labelX: Float, valueX: Float, y: Float,
        label: String, value: Double, rowH: Float
    ): Float {
        paint.textAlign = Paint.Align.LEFT
        paint.color = ExportStyle.INK
        canvas.drawText(label, labelX + 10f, y, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(fmt(value), valueX, y, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT

        // Лёгкая разделительная линия
        paint.color = ExportStyle.MUTED
        paint.alpha = 40
        canvas.drawLine(labelX + 10f, y + 6f, valueX, y + 6f, paint)
        paint.alpha = 255

        return y + rowH
    }

    private fun fmt(value: Double): String = MoneyFormatter.formatByn(value)

    private fun formatPercent(value: Int): String = "$value%"
}
