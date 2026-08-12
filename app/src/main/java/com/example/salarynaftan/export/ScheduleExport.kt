package com.example.salarynaftan.export

import com.example.salarynaftan.ShiftSchedule
import com.example.salarynaftan.ScheduleType
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale


// ==========================================
// ФУНКЦИИ ЭКСПОРТА ГРАФИКА
// ==========================================
fun generateScheduleText(
    month: YearMonth,
    brigade: Int,
    scheduleType: ScheduleType
): String {
    val sb = StringBuilder()
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("ru")).replaceFirstChar { it.uppercase() }
    sb.append("🗓 График смен на $monthName ${month.year} (Бригада $brigade):\n\n")

    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        val shift = ShiftSchedule.shiftFor(date, brigade, scheduleType)
        val dayOfWeekIndex = date.dayOfWeek.value - 1
        val dayOfWeekStr = weekDays[dayOfWeekIndex]

        sb.append("$day ($dayOfWeekStr) — ${shift.displayName}\n")
    }
    return sb.toString()
}

fun shareScheduleText(
    context: android.content.Context,
    month: YearMonth,
    brigade: Int,
    scheduleType: ScheduleType
) {
    val text = generateScheduleText(month, brigade, scheduleType)
    val intent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = android.content.Intent.createChooser(intent, "Отправить график смен")
    try {
        context.startActivity(shareIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        // Нет приложения для отправки
    }
}
