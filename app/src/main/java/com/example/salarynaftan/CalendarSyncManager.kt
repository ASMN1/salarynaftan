package com.example.salarynaftan

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import com.example.salarynaftan.data.DataStoreManager
import kotlinx.coroutines.delay
import timber.log.Timber
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
    suspend fun syncMonthToCalendar(
        context: Context,
        month: YearMonth,
        brigade: Int,
        scheduleType: ScheduleType
    ): Int {
        val calendarId = getPrimaryCalendarId(context) ?: return -1
        // Сохраняем ID календаря, чтобы удаление шло по тому же календарю,
        // что и добавление (п.4.2 аудита) — иначе при смене календаря по
        // умолчанию старые события останутся и появятся дубликаты.
        DataStoreManager.getInstance(context).saveCalendarId(calendarId)
        // Сначала удаляем старые события за этот месяц, чтобы не было дубликатов
        // при повторной синхронизации (п.6.8 аудита).
        removeMonthFromCalendar(context, month, brigade)
        val insertedEventIds = mutableListOf<Long>()

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val shift = try {
                ShiftSchedule.shiftFor(date, brigade, scheduleType)
            } catch (_: IllegalArgumentException) {
                Timber.w("Невалидная бригада %d для графика %s — смена дня %d пропущена", brigade, scheduleType, day)
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

            // Точечный retry вставки одного события (п.6.1 аудита): ContentResolver
            // может временно вернуть null при занятости CalendarProvider. Ретраим
            // ТОЛЬКО отдельный insert, а не всю месячную операцию — повтор всей
            // операции мог бы создать дубли, если часть событий уже вставлена.
            val uri = insertWithRetry(context, values, maxAttempts = 3)
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
        // Используем сохранённый ID календаря, если он есть; иначе — текущий
        // календарь по умолчанию (п.4.2 аудита).
        val calendarId = DataStoreManager.getInstance(context).getCalendarId()
            ?: getPrimaryCalendarId(context) ?: return 0
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

    /**
     * Вставка одного события с повторами (п.6.1 аудита).
     * Ретрай изолирован на уровне одного insert: повтор всей операции
     * мог бы создать дубликаты при частичном успехе первой попытки.
     */
    private suspend fun insertWithRetry(context: Context, values: ContentValues, maxAttempts: Int): Uri? {
        var lastError: Exception? = null
        var attempt = 0
        while (attempt < maxAttempts) {
            try {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                if (uri != null) return uri
            } catch (e: Exception) {
                lastError = e
            }
            // Короткая пауза между попытками (как Thread.sleep, но без блокировки потока).
            try { delay(200L * (1L shl attempt)) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            attempt++
        }
        // Если все попытки вернули null без исключения — логируем для диагностики (п.1.5).
        if (lastError == null) {
            Timber.w("insert вернул null после $maxAttempts попыток")
        }
        return null
    }

    /** Возвращает ID календаря по умолчанию (Google или локальный). */
    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        // Ищем календарь с полным доступом OWNER, чтобы удаление/изменение событий
        // гарантированно работало (п.4.2 аудита). Если OWNER-календаря нет (например,
        // на некоторых OEM есть только CONTRIBUTOR) — падаем до CONTRIBUTOR.
        // Не требуем IS_PRIMARY/VISIBLE — на многих устройствах нет «основного» календаря.
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC"

        // 1) Сначала пробуем OWNER.
        queryFirstCalendarId(context, projection, sortOrder, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            ?.let { return it }
        // 2) Фолбэк: любой календарь, куда можно писать (CONTRIBUTOR+).
        return queryFirstCalendarId(context, projection, sortOrder, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
    }

    private fun queryFirstCalendarId(
        context: Context,
        projection: Array<String>,
        sortOrder: String,
        minAccessLevel: Int
    ): Long? {
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(minAccessLevel.toString())
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Координатор use-case синхронизации, отделённый от UI и Android API. */
object CalendarSyncCoordinator {
    suspend fun syncMonth(
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