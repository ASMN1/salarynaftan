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

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PreferenceKeys.ALARM_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AlarmScheduler"

        // Label сериализуется через Base64: разделители | и ; внутри текста
        // иначе ломают парсинг списка будильников (split происходил до
        // раскодирования, поэтому простое экранирование не спасало).
        // Base64-алфавит не содержит ни |, ни ; — разбиение всегда безопасно.
        // Для обратной совместимости со старыми «плоскими» метками (без спецсимволов)
        // при неудачном раскодировании возвращаем исходную строку.
        private fun encodeLabel(label: String): String =
            java.util.Base64.getEncoder().encodeToString(label.toByteArray(Charsets.UTF_8))

        private fun decodeLabel(encoded: String): String =
            try {
                String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                encoded
            }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Тестовый будильник (№19 из UI/UX): ставит настоящий сигнал через указанные
     * секунды (по умолчанию 10), чтобы пользователь убедился, что звук, вибрация
     * и разрешения (exact alarm) работают. Вызывает тот же AlarmReceiver, что и
     * настоящий будильник. Возвращает false, если точные будильники запрещены.
     */
    fun scheduleTestAlarm(delaySeconds: Int = 10): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", "🔔 Тест будильника")
            action = "com.example.salarynaftan.TEST_ALARM"
        }
        // Уникальный requestCode, чтобы не конфликтовать с реальными будильниками
        val requestCode = 100000 + (System.currentTimeMillis() % 90000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка установки тестового будильника", e)
            return false
        }
    }

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

            // Ограничиваем поиск 11 днями (10-дневный цикл гарантирует все типы смен)
            var attempts = 0
            while (ShiftSchedule.shiftFor(targetDate, brigade) != type && attempts < 11) {
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
            Log.e(TAG, "Ошибка установки будильника для смены", e)
        }
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

    fun cancelAllRegularAlarms() {
        getRegularAlarms().forEach { cancelSingleRegularAlarm(it.id) }
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

    fun getRegularAlarms(): List<RegularAlarm> {
        val raw = prefs.getString(PreferenceKeys.REGULAR_ALARMS, null) ?: return listOf(
            RegularAlarm(1L, "07:30", false, "Утренний"),
            RegularAlarm(2L, "21:00", false, "Вечерний")
        )
        return raw.split(";").mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 4) {
                RegularAlarm(
                    id = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
                    time = parts[1],
                    isEnabled = parts[2].toBoolean(),
                    label = decodeLabel(parts[3])
                )
            } else null
        }
    }

    fun saveRegularAlarms(alarms: List<RegularAlarm>) {
        val serialized = alarms.joinToString(";") {
            "${it.id}|${it.time}|${it.isEnabled}|${encodeLabel(it.label)}"
        }
        prefs.edit().putString(PreferenceKeys.REGULAR_ALARMS, serialized).apply()

        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                scheduleSingleRegularAlarm(alarm)
            } else {
                cancelSingleRegularAlarm(alarm.id)
            }
        }
    }

    fun scheduleSingleRegularAlarm(alarm: RegularAlarm) {
        val parts = alarm.time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = LocalDateTime.now()
        var targetTime = now.withHour(hour).withMinute(minute).withSecond(0)
        if (targetTime.isBefore(now)) {
            targetTime = targetTime.plusDays(1)
        }

        val triggerMillis = targetTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", alarm.label)
        }

        val requestCode = (alarm.id % Int.MAX_VALUE).toInt()
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

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    triggerMillis,
                    showPendingIntent
                )

                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка установки обычного будильника", e)
        }
    }

    fun cancelSingleRegularAlarm(alarmId: Long) {
        val requestCode = (alarmId % Int.MAX_VALUE).toInt()
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun updateAutoSilenceAlarms(isEnabled: Boolean, startTime: String, endTime: String) {
        val intentOn = Intent(context, SilentModeReceiver::class.java).apply { action = PreferenceKeys.ACTION_SILENT_ON }
        val intentOff = Intent(context, SilentModeReceiver::class.java).apply { action = PreferenceKeys.ACTION_SILENT_OFF }

        val piOn = PendingIntent.getBroadcast(context, 90001, intentOn, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piOff = PendingIntent.getBroadcast(context, 90002, intentOff, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(piOn)
        alarmManager.cancel(piOff)

        if (isEnabled) {
            val now = LocalDateTime.now()
            val hOn = startTime.substringBefore(":").toIntOrNull() ?: 8
            val mOn = startTime.substringAfter(":").toIntOrNull() ?: 0
            var startLdt = now.withHour(hOn).withMinute(mOn).withSecond(0)
            if (startLdt.isBefore(now)) startLdt = startLdt.plusDays(1)

            val hOff = endTime.substringBefore(":").toIntOrNull() ?: 16
            val mOff = endTime.substringAfter(":").toIntOrNull() ?: 0
            var endLdt = now.withHour(hOff).withMinute(mOff).withSecond(0)
            if (endLdt.isBefore(now)) endLdt = endLdt.plusDays(1)
            // Если время окончания раньше или равно началу (например, тишина 23:00–07:00),
            // сдвигаем окончание на ещё один день, иначе OFF сработает раньше ON.
            if (endLdt.isBefore(startLdt) || endLdt == startLdt) endLdt = endLdt.plusDays(1)

            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        startLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        piOn
                    )

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        piOff
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка установки авто-тишины", e)
            }
        }
    }

    fun rescheduleAllAfterBoot() {
        cancelAllShiftAlarmsAcrossAllBrigades()
        cancelAllRegularAlarms()

        // Каждый будильник планируется в изолированном runCatching: исключение
        // на одном (например, на конкретном OEM) не должно останавливать
        // восстановление остальных будильников (BUG-003).
        fun <T> attempt(label: String, block: () -> T) {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка восстановления будильника ($label)", e)
            }
        }

        // Восстанавливаем будильники для ВСЕХ бригад, у которых они были включены,
        // а не только для текущей. Ключи хранятся в формате "shift_alarm_<brigade>_<type>".
        for (b in 1..5) {
            ShiftType.entries.forEach { type ->
                if (isAlarmScheduledForShift(type, b)) {
                    attempt("сменная $b/${type.name}") {
                        scheduleAlarmsForShift(type, b)
                    }
                }
            }
        }
        getRegularAlarms().forEach { alarm ->
            if (alarm.isEnabled) {
                attempt("обычный ${alarm.id}") {
                    scheduleSingleRegularAlarm(alarm)
                }
            }
        }
        val autoPrefs = context.getSharedPreferences(PreferenceKeys.AUTO_SILENCE_PREFS, Context.MODE_PRIVATE)
        if (autoPrefs.getBoolean(PreferenceKeys.AUTO_SILENCE_ENABLED, false)) {
            val start = autoPrefs.getString(PreferenceKeys.AUTO_SILENCE_START, "08:00") ?: "08:00"
            val end = autoPrefs.getString(PreferenceKeys.AUTO_SILENCE_END, "16:00") ?: "16:00"
            attempt("авто-тишина") {
                updateAutoSilenceAlarms(true, start, end)
            }
        }
    }
}