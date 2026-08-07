package com.example.salarynaftan.export

import com.example.salarynaftan.ShiftSchedule
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
    fun createIcsFile(context: Context, month: YearMonth, brigade: Int): File? {
        val monthName = month.month.toString().lowercase().replaceFirstChar { it.uppercase() }
        val events = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//SalaryNaftan//Calendar Export//RU")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("X-WR-CALNAME:График смен $monthName ${month.year} (бригада $brigade)")
            appendLine("X-WR-CALDESC:График смен ОАО «Нафтан» — бригада $brigade")
            appendLine("X-WR-TIMEZONE:Europe/Minsk")

            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val shift = ShiftSchedule.shiftFor(date, brigade)
                if (shift == ShiftType.OFF) continue

                val sTime = shift.startTime ?: continue
                val eTime = shift.endTime ?: continue

                val dateStr = date.format(DATE_FORMAT)
                val sh = sTime.hour.toString().padStart(2, '0')
                val sm = sTime.minute.toString().padStart(2, '0')
                val eh = eTime.hour.toString().padStart(2, '0')
                val em = eTime.minute.toString().padStart(2, '0')

                // Если смена ночная (Н: 0:00–8:00), дата окончания = следующий день
                val endDateStr = if (shift == ShiftType.NIGHT) {
                    date.plusDays(1).format(DATE_FORMAT)
                } else {
                    dateStr
                }

                val uid = "shift-${dateStr}-b${brigade}-${shift.name}@salarynaftan"

                appendLine("BEGIN:VEVENT")
                appendLine("DTSTART;TZID=Europe/Minsk:${dateStr}T${sh}${sm}00")
                appendLine("DTEND;TZID=Europe/Minsk:${endDateStr}T${eh}${em}00")
                appendLine("SUMMARY:${shift.displayName} смена (бригада $brigade)")
                appendLine("DESCRIPTION:${shift.displayName} смена · $sTime–$eTime · Бригада $brigade · ОАО «Нафтан»")
                appendLine("LOCATION:ОАО «Нафтан»")
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

    /** Шарит .ics файл через системный Intent. */
    fun shareIcs(context: Context, month: YearMonth, brigade: Int): Boolean {
        val file = createIcsFile(context, month, brigade) ?: return false
        return shareFile(context, file, "text/calendar", "Добавить в календарь")
    }
}