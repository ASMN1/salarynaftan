package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Сменные будильники: включение/выключение, расписания по времени, выбор
 * следующего подходящего дня смены, смена активной бригады.
 * Отделено от AlarmScheduler (п.3.3): этот класс отвечает только за сменный
 * распорядок и не знает об обычных будильниках или авто-тишине.
 */
class ShiftAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PreferenceKeys.ALARM_PREFS, Context.MODE_PRIVATE)

    fun isAlarmScheduledForShift(type: ShiftType, brigade: Int): Boolean {
        return prefs.getBoolean("${PreferenceKeys.SHIFT_ALARM_ENABLED_PREFIX}${brigade}_${type.name}", false)
    }

    fun getAlarmTimesForShift(type: ShiftType, brigade: Int): List<String> {
        val defaultList = when (type) {
            ShiftType.MORNING -> "06:00"
            ShiftType.DAY -> "14:00"
            ShiftType.NIGHT -> "22:00"
            ShiftType.OFF -> "08:00"
        }
        val saved = prefs.getString("${PreferenceKeys.SHIFT_TIMES_PREFIX}${brigade}_${type.name}", defaultList) ?: defaultList
        return saved.split(",").filter { it.isNotBlank() }
    }

    fun saveAlarmTimesForShift(type: ShiftType, times: List<String>, brigade: Int) {
        prefs.edit().putString("${PreferenceKeys.SHIFT_TIMES_PREFIX}${brigade}_${type.name}", times.joinToString(",")).apply()
    }

    fun scheduleAlarmsForShift(type: ShiftType, brigade: Int): Int {
        cancelAlarmsForShiftQuiet(type, brigade)

        prefs.edit()
            .putBoolean("${PreferenceKeys.SHIFT_ALARM_ENABLED_PREFIX}${brigade}_${type.name}", true)
            .apply()

        val times = getAlarmTimesForShift(type, brigade)

        times.forEachIndexed { index, timeStr ->
            scheduleSingleShiftAlarm(
                type = type,
                brigade = brigade,
                index = index,
                timeStr = timeStr
            )
        }

        return times.size
    }

    fun scheduleSingleShiftAlarm(
        type: ShiftType,
        brigade: Int,
        index: Int,
        timeStr: String
    ) {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 6
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        var targetDate = LocalDate.now()
        val nowTime = LocalTime.now()
        val candidateToday = LocalTime.of(hour, minute)

        if (ShiftSchedule.shiftFor(targetDate, brigade) != type || !candidateToday.isAfter(nowTime)) {
            targetDate = targetDate.plusDays(1)

            // Ищем до конца цикла смен: длина цикла гарантирует, что нужный
            // тип смены встретится за один полный проход (детерминированный
            // поиск вместо жёстких 11 попыток).
            var attempts = 0
            while (ShiftSchedule.shiftFor(targetDate, brigade) != type &&
                attempts < ShiftSchedule.SHIFT_CYCLE_SIZE
            ) {
                targetDate = targetDate.plusDays(1)
                attempts++
            }
        }

        val targetMillis = LocalDateTime.of(
            targetDate,
            LocalTime.of(hour, minute)
        ).atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", "Бр $brigade: ${type.displayName} смена")
            putExtra("shift_type_name", type.name)
            putExtra("alarm_index", index)
            putExtra("brigade", brigade)
            putExtra("alarm_time", timeStr)
        }

        val requestCode = brigade * 1000 + type.ordinal * 100 + index

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("selected_tab", 3)
        }

        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(targetMillis, showPendingIntent)

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(info, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("ShiftAlarmScheduler", "Ошибка установки будильника для смены", e)
        }

        // Пред-напоминание о смене (п.6.7): если включено, ставим отдельный
        // сигнал за N минут до срабатывания основного будильника.
        val reminderLead = SettingsManager(context).getShiftReminderMinutes()
        if (reminderLead > 0) {
            scheduleReminderAt(type, brigade, index, timeStr, targetDate, reminderLead)
        }
    }

    private fun scheduleReminderAt(
        type: ShiftType,
        brigade: Int,
        index: Int,
        timeStr: String,
        targetDate: LocalDate,
        leadMinutes: Int
    ) {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 6
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val reminderMillis = LocalDateTime.of(targetDate, LocalTime.of(hour, minute))
            .minusMinutes(leadMinutes.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        // Не планируем напоминание в прошлом: если сигнал уже близко, пусть
        // сработает только основной будильник.
        if (reminderMillis <= System.currentTimeMillis()) return

        val rIntent = Intent(context, ShiftReminderReceiver::class.java).apply {
            action = "com.example.salarynaftan.ACTION_SHIFT_REMINDER"
            putExtra("shift_type_name", type.name)
            putExtra("brigade", brigade)
            putExtra("alarm_index", index)
            putExtra("alarm_time", timeStr)
        }
        // Отдельный диапазон requestCode, чтобы не конфликтовать с основными
        // будильниками (brigade*1000 + type.ordinal*100 + index) и тестовым.
        val rCode = 500000 + brigade * 1000 + type.ordinal * 100 + index
        val rPI = PendingIntent.getBroadcast(
            context,
            rCode,
            rIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderMillis, rPI)
            }
        } catch (e: Exception) {
            Log.e("ShiftAlarmScheduler", "Ошибка установки пред-напоминания", e)
        }
    }

    /**
     * Перепланирование пред-напоминания на следующий подходящий день смены.
     * Вызывается из ShiftReminderReceiver после срабатывания, чтобы напоминание
     * «поехало» дальше (п.6.7). Напоминание уже сработало, поэтому ищем следующий
     * день (начиная с завтра), в который выпадает эта смена.
     */
    fun rescheduleShiftReminder(type: ShiftType, brigade: Int, index: Int, timeStr: String) {
        val lead = SettingsManager(context).getShiftReminderMinutes()
        if (lead <= 0) return
        var targetDate = LocalDate.now().plusDays(1)
        var attempts = 0
        while (ShiftSchedule.shiftFor(targetDate, brigade) != type &&
            attempts < ShiftSchedule.SHIFT_CYCLE_SIZE
        ) {
            targetDate = targetDate.plusDays(1)
            attempts++
        }
        scheduleReminderAt(type, brigade, index, timeStr, targetDate, lead)
    }

    private fun cancelReminderForIndex(type: ShiftType, brigade: Int, index: Int) {
        val rCode = 500000 + brigade * 1000 + type.ordinal * 100 + index
        val rIntent = Intent(context, ShiftReminderReceiver::class.java).apply {
            action = "com.example.salarynaftan.ACTION_SHIFT_REMINDER"
        }
        val rPI = PendingIntent.getBroadcast(
            context,
            rCode,
            rIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(rPI)
    }

    private fun cancelAlarmsForShiftQuiet(type: ShiftType, brigade: Int) {
        for (index in 0 until 10) {
            val requestCode = brigade * 1000 + type.ordinal * 100 + index
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            // Гасим и соответствующее пред-напоминание (п.6.7), чтобы оно не
            // осталось «висеть» после отмены сменного будильника.
            cancelReminderForIndex(type, brigade, index)
        }
    }

    fun cancelAlarmsForShift(type: ShiftType, brigade: Int) {
        prefs.edit().putBoolean("${PreferenceKeys.SHIFT_ALARM_ENABLED_PREFIX}${brigade}_${type.name}", false).apply()
        cancelAlarmsForShiftQuiet(type, brigade)
    }

    fun cancelAllShiftAlarmsAcrossAllBrigades() {
        for (b in 1..5) {
            ShiftType.entries.forEach { type ->
                cancelAlarmsForShiftQuiet(type, b)
            }
        }
    }

    /**
     * Перепланирование сменного будильника на следующий подходящий день.
     * Вызывается при срабатывании (AlarmReceiver), чтобы будильник «поехал» дальше.
     */
    fun rescheduleShiftAlarmAfterRing(
        shiftType: ShiftType,
        brigade: Int,
        index: Int,
        timeStr: String
    ) {
        if (isAlarmScheduledForShift(shiftType, brigade)) {
            scheduleSingleShiftAlarm(
                type = shiftType,
                brigade = brigade,
                index = index,
                timeStr = timeStr
            )
        }
    }

    fun rescheduleAllAlarmsForBrigade(brigade: Int) {
        // scheduleAlarmsForShift сам отменяет предыдущие, поэтому достаточно
        // только заново запланировать те смены, у которых будильники включены.
        ShiftType.entries.forEach { type ->
            if (isAlarmScheduledForShift(type, brigade)) {
                scheduleAlarmsForShift(type, brigade)
            }
        }
    }

    /**
     * Переключение активной бригады: сменные будильники имеют смысл только для
     * одной (активной) бригады пользователя, поэтому при смене бригады гасим
     * ВСЕ сменные будильники и заново ставим только для новой бригады
     * (по включённым флагам). Иначе пользователь получал бы «чужие» будильники
     * от старых бригад (п.4.4).
     */
    fun switchActiveBrigade(newBrigade: Int) {
        cancelAllShiftAlarmsAcrossAllBrigades()
        ShiftType.entries.forEach { type ->
            if (isAlarmScheduledForShift(type, newBrigade)) {
                scheduleAlarmsForShift(type, newBrigade)
            }
        }
    }
}
