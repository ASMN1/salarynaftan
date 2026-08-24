package com.example.salarynaftan.export

import com.example.salarynaftan.ShiftSchedule
import com.example.salarynaftan.ScheduleType
import com.example.salarynaftan.ShiftType
import com.example.salarynaftan.util.getExportDir
import com.example.salarynaftan.util.shareFile
import android.content.Context
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Экспорт графика смен в формат .ics (iCalendar) для импорта в Google Calendar,
 * Apple Calendar и другие календари.
 */
object ScheduleIcsExporter {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Генерирует .ics файл с событиями смен на указанный месяц для заданной бригады.
     * Каждая смена становится отдельным событием с временем начала/окончания.
     * Выходные дни не добавляются.
     */
    fun createIcsFile(context: Context, month: YearMonth, brigade: Int, scheduleType: ScheduleType): File? {
        val monthName = month.month.toString().lowercase().replaceFirstChar { it.uppercase() }
        // Часовой пояс берётся из системы, а не хардкод Europe/Minsk (п.5.3 аудита).
        val tzId = java.util.TimeZone.getDefault().id
        val events = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//SalaryNaftan//Calendar Export//RU")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("X-WR-CALNAME:${escape("График смен $monthName ${month.year} (бригада $brigade)")}")
            appendLine("X-WR-CALDESC:${escape("График смен ОАО «Нафтан» — бригада $brigade")}")
            appendLine("X-WR-TIMEZONE:$tzId")

            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val shift = ShiftSchedule.shiftFor(date, brigade, scheduleType)
                if (shift == ShiftType.OFF) continue

                // Времена смены зависят от активного графика (в №2 смены 12-часовые).
                val sTime = ShiftSchedule.shiftStartTime(shift, scheduleType) ?: continue
                val eTime = ShiftSchedule.shiftEndTime(shift, scheduleType) ?: continue

                val dateStr = date.format(DATE_FORMAT)
                val sh = sTime.hour.toString().padStart(2, '0')
                val sm = sTime.minute.toString().padStart(2, '0')
                val eh = eTime.hour.toString().padStart(2, '0')
                val em = eTime.minute.toString().padStart(2, '0')

                // Если смена ночная (пересекает полночь, например Н: 0:00–8:00 в №1
                // и 20:00–8:00 в №2), дата окончания = следующий день
                val endDateStr = if (eTime.isBefore(sTime) || eTime == sTime) {
                    date.plusDays(1).format(DATE_FORMAT)
                } else {
                    dateStr
                }

                val uid = "shift-${dateStr}-b${brigade}-${shift.name}@salarynaftan"

                appendLine("BEGIN:VEVENT")
                appendLine("DTSTART;TZID=$tzId:${dateStr}T${sh}${sm}00")
                appendLine("DTEND;TZID=$tzId:${endDateStr}T${eh}${em}00")
                appendLine("SUMMARY:${escape("${shift.displayName} смена (бригада $brigade)")}")
                appendLine("DESCRIPTION:${escape("${shift.displayName} смена · $sTime–$eTime · Бригада $brigade · ОАО «Нафтан»")}")
                appendLine("LOCATION:${escape("ОАО «Нафтан»")}")
                appendLine("UID:$uid")
                appendLine("DTSTAMP:${java.time.LocalDate.now().format(DATE_FORMAT)}T000000Z")
                appendLine("TRANSP:OPAQUE")
                appendLine("END:VEVENT")
            }
            appendLine("END:VCALENDAR")
        }

        if (!events.contains("BEGIN:VEVENT")) return null

        val file = File(getExportDir(context), "schedule_b${brigade}_${month.year}_${month.monthValue}.ics")
        file.writeText(events)
        return file
    }

    /** RFC 5545 TEXT escaping (backslash, comma, semicolon and line breaks). */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    /** Шарит .ics файл через системный Intent. */
    fun shareIcs(
        context: Context,
        month: YearMonth,
        brigade: Int,
        scheduleType: ScheduleType
    ): Boolean {
        val file = createIcsFile(context, month, brigade, scheduleType) ?: return false
        return shareFile(context, file, "text/calendar", "Добавить в календарь")
    }
}