package com.example.salarynaftan

import android.graphics.Color

/**
 * Общий визуальный стиль для экспорта графика (PDF и картинка), чтобы оба
 * выглядели как один и тот же документ, а не два независимых дизайна.
 *
 * Цвета самих смен (жёлтый/зелёный/синий/бежевый) тут НЕ продублированы —
 * оба экспортёра берут их напрямую из ShiftType.color, того же значения,
 * что красит календарь в самом приложении. Буквы смен тоже раньше были
 * продублированы одинаковым when-блоком в обоих файлах — вынесены сюда.
 */
object ExportStyle {
    val PAPER = Color.WHITE
    val INK = Color.rgb(38, 40, 43)
    val MUTED = Color.rgb(138, 138, 142)
    val SUNDAY = Color.rgb(200, 70, 70)
    val ACCENT = Color.rgb(0, 230, 118)        // фирменный зелёный, как во всём приложении
    val ACCENT_DARK = Color.rgb(0, 59, 34)
    val ACCENT_MUTED = Color.rgb(200, 230, 215)

    fun textColorFor(shift: ShiftType): Int =
        if (shift == ShiftType.NIGHT) Color.WHITE else INK

    fun shiftLetter(shift: ShiftType): String = shift.shortName

    fun shiftLabel(shift: ShiftType): String = when (shift) {
        ShiftType.DAY -> "День"
        ShiftType.NIGHT -> "Ночь"
        ShiftType.MORNING -> "Утро"
        ShiftType.OFF -> "Выходной"
    }

    val LEGEND_ORDER = listOf(ShiftType.MORNING, ShiftType.DAY, ShiftType.NIGHT, ShiftType.OFF)
}
