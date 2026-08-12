package com.example.salarynaftan

import java.time.LocalDate
import java.time.YearMonth

/** Pure widget projection; contains no RemoteViews or Android framework API. */
data class WidgetCellModel(
    val day: Int,
    val date: LocalDate,
    val shift: ShiftType,
    val isToday: Boolean,
    val isSalary: Boolean,
    val isAdvance: Boolean,
    val isHoliday: Boolean
)

object WidgetScheduleModel {
    fun forMonth(today: LocalDate, brigade: Int, scheduleType: ScheduleType): List<WidgetCellModel?> {
        val month = YearMonth.from(today)
        val salaryDate = adjustedPayDate(10, month)
        val advanceDate = adjustedPayDate(25, month)
        val emptyBefore = month.atDay(1).dayOfWeek.value - 1
        return (0 until 42).map { index ->
            val day = index - emptyBefore + 1
            if (day !in 1..month.lengthOfMonth()) return@map null
            val date = month.atDay(day)
            WidgetCellModel(
                day = day,
                date = date,
                shift = ShiftSchedule.shiftFor(date, brigade, scheduleType),
                isToday = date == today,
                isSalary = date == salaryDate,
                isAdvance = date == advanceDate,
                isHoliday = Holidays.isHoliday(date)
            )
        }
    }

    private fun adjustedPayDate(day: Int, month: YearMonth): LocalDate {
        var date = month.atDay(day)
        while (date.dayOfWeek.value > 5) date = date.minusDays(1)
        return date
    }
}