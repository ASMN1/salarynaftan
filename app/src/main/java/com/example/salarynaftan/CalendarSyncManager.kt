package com.example.salarynaftan

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Синхронизация графика смен с системным календарём (Google Calendar / локальный).
 *
 * Использует CalendarProvider — стандартный Android API, который добавляет
 * события в календарь, выбранный пользователем по умолчанию (обычно Google).
 * Каждая смена становится отдельным событием с временем начала/окончания.
 *
 * Требует разрешения WRITE_CALENDAR в манифесте.
 */
object CalendarSyncManager {

    private const val OWNED_MARKER = "X-SALARYNAFTAN-OWNED"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Добавляет смены месяца в календарь по умолчанию.
     * @param scheduleType тип графика, выбранный на экране (не глобальный),
     *        чтобы расчёт смен совпадал с тем, что видит пользователь.
     * @return количество добавленных событий, или -1 если календарь недоступен.
     */
    fun syncMonthToCalendar(
        context: Context,
        month: YearMonth,
        brigade: Int,
        scheduleType: ScheduleType
    ): Int {
        val calendarId = getPrimaryCalendarId(context) ?: return -1
        // Сначала удаляем старые события за этот месяц, чтобы не было дубликатов
        // при повторной синхронизации (п.6.8 аудита).
        removeMonthFromCalendar(context, month, brigade)
        val insertedEventIds = mutableListOf<Long>()

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val shift = try {
                ShiftSchedule.shiftFor(date, brigade, scheduleType)
            } catch (_: IllegalArgumentException) {
                // Невалидная бригада для выбранного графика — не роняем приложение.
                continue
            }
            if (shift == ShiftType.OFF) continue

            val sTime = ShiftSchedule.shiftStartTime(shift, scheduleType) ?: continue
            val eTime = ShiftSchedule.shiftEndTime(shift, scheduleType) ?: continue

            val startMillis = date.atTime(sTime).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endDate = if (eTime.isBefore(sTime) || eTime == sTime) date.plusDays(1) else date
            val endMillis = endDate.atTime(eTime).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "${shift.displayName} смена (бригада $brigade)")
                put(CalendarContract.Events.DESCRIPTION, "$OWNED_MARKER; бригада=$brigade; ${shift.displayName} смена · $sTime–$eTime · ОАО «Нафтан»")
                put(CalendarContract.Events.EVENT_LOCATION, "ОАО «Нафтан»")
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = try {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            } catch (_: Exception) {
                // CalendarProvider не поддерживает общую транзакцию через
                // ContentResolver, поэтому coordinator компенсирует уже
                // вставленные события при частичном сбое.
                null
            }
            val eventId = try {
                uri?.let(ContentUris::parseId)
            } catch (_: Exception) {
                null
            }
            if (eventId != null) {
                insertedEventIds += eventId
                // Напоминание за 30 минут до смены
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 30)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                val reminderInserted = try {
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues) != null
                } catch (_: Exception) {
                    false
                }
                if (!reminderInserted) {
                    rollbackInsertedEvents(context, insertedEventIds)
                    return -1
                }
            } else {
                rollbackInsertedEvents(context, insertedEventIds)
                return -1
            }
        }
        return insertedEventIds.size
    }

    private fun rollbackInsertedEvents(context: Context, eventIds: List<Long>) {
        eventIds.asReversed().forEach { id ->
            try {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    null,
                    null
                )
            } catch (_: Exception) {
                // Компенсация best-effort: CalendarProvider может стать
                // недоступен именно во время rollback.
            }
        }
    }

    /** Удаляет только события, созданные этим приложением.
     * @return количество удалённых событий.
     */
    fun removeMonthFromCalendar(context: Context, month: YearMonth, brigade: Int): Int {
        val calendarId = getPrimaryCalendarId(context) ?: return 0
        var removed = 0

        val startMillis = month.atDay(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = month.atEndOfMonth().plusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.DTSTART} >= ? AND " +
                "${CalendarContract.Events.DTSTART} < ? AND " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ?"
        val selectionArgs = arrayOf(
            calendarId.toString(),
            startMillis.toString(),
            endMillis.toString(),
            "%$OWNED_MARKER%; бригада=$brigade;%"
        )

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                    try {
                        context.contentResolver.delete(deleteUri, null, null)
                        removed++
                    } catch (_: Exception) {
                        // Один неудачный delete не должен прерывать остальные.
                    }
                }
            }
        } catch (_: Exception) {
            // Календарь недоступен или нет разрешения — не роняем приложение.
        }
        return removed
    }

    /** Возвращает ID календаря по умолчанию (Google или локальный). */
    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        // Ищем любой календарь, в который можно писать (уровень доступа >= CONTRIBUTOR).
        // Не требуем IS_PRIMARY/VISIBLE — на многих устройствах нет «основного» календаря,
        // и строгий запрос возвращал null → «Календарь недоступен».
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC"

        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Координатор use-case синхронизации, отделённый от UI и Android API. */
object CalendarSyncCoordinator {
    fun syncMonth(
        context: Context,
        month: YearMonth,
        brigade: Int,
        scheduleType: ScheduleType
    ): CalendarSyncResult {
        val count = CalendarSyncManager.syncMonthToCalendar(context, month, brigade, scheduleType)
        return CalendarSyncResult.fromCount(count)
    }
}

sealed interface CalendarSyncResult {
    data class Success(val added: Int) : CalendarSyncResult
    data object Failed : CalendarSyncResult

    companion object {
        fun fromCount(count: Int): CalendarSyncResult =
            if (count < 0) Failed else Success(count)
    }
}