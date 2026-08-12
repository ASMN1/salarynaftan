package com.example.salarynaftan

import android.graphics.Color
import android.widget.RemoteViews

/** Android widget rendering boundary. Schedule calculation lives in WidgetScheduleModel. */
class WidgetRenderer {
    private val salaryColor = 0xFFFFD600.toInt()
    private val advanceColor = 0xFF00BFA5.toInt()
    private val holidayColor = 0xFFE040FB.toInt()
    private val textNight = 0xB3FFFFFF.toInt()
    private val textDark = 0xFF222222.toInt()
    private val textOff = 0xFF555555.toInt()

    fun renderCells(
        views: RemoteViews,
        models: List<WidgetCellModel?>,
        cellIds: Array<IntArray>,
        cellNumIds: Array<IntArray>,
        cellShiftIds: Array<IntArray>,
        colors: WidgetColors
    ) {
        models.forEachIndexed { index, model ->
            val row = index / 7
            val col = index % 7
            val cellId = cellIds[row][col]
            val numId = cellNumIds[row][col]
            val shiftId = cellShiftIds[row][col]
            if (cellId == 0 || numId == 0 || shiftId == 0) return@forEachIndexed
            if (model == null) {
                views.setInt(cellId, "setVisibility", android.view.View.INVISIBLE)
                return@forEachIndexed
            }
            views.setInt(cellId, "setVisibility", android.view.View.VISIBLE)
            val shiftColor = when (model.shift) {
                ShiftType.MORNING -> colors.morning
                ShiftType.DAY -> colors.day
                ShiftType.NIGHT -> colors.night
                ShiftType.OFF -> colors.off
            }
            val background = when {
                model.isToday -> overDark(colors.primary, 0.35f)
                model.isHoliday -> overDark(holidayColor, 0.28f)
                model.isSalary -> overDark(salaryColor, 0.25f)
                model.isAdvance -> overDark(advanceColor, 0.25f)
                else -> overDark(shiftColor, 0.85f)
            }
            views.setInt(cellId, "setBackgroundResource", R.drawable.widget_cell_round)
            views.setColorStateList(
                cellId,
                "setBackgroundTintList",
                android.content.res.ColorStateList.valueOf(background)
            )
            views.setTextViewText(numId, model.day.toString())
            views.setTextColor(numId, when {
                model.isToday -> colors.primary
                model.shift == ShiftType.NIGHT -> Color.WHITE
                model.shift == ShiftType.OFF -> textOff
                else -> textDark
            })
            val label = when {
                model.isSalary -> "💰 ${model.shift.shortName}"
                model.isAdvance -> "💵 ${model.shift.shortName}"
                model.isHoliday -> "🎉 ${model.shift.shortName}"
                else -> model.shift.shortName
            }
            views.setTextViewText(shiftId, label)
            views.setTextColor(shiftId, when {
                model.isToday -> colors.primary
                model.isSalary || model.isAdvance -> Color.BLACK
                model.shift == ShiftType.NIGHT -> textNight
                model.shift == ShiftType.OFF -> textOff
                else -> textDark
            })
        }
    }

    private fun overDark(color: Int, alpha: Float): Int = Color.rgb(
        (Color.red(color) * alpha + 0x12 * (1 - alpha)).toInt().coerceIn(0, 255),
        (Color.green(color) * alpha + 0x12 * (1 - alpha)).toInt().coerceIn(0, 255),
        (Color.blue(color) * alpha + 0x12 * (1 - alpha)).toInt().coerceIn(0, 255)
    )
}

data class WidgetColors(
    val morning: Int,
    val day: Int,
    val night: Int,
    val off: Int,
    val primary: Int
)