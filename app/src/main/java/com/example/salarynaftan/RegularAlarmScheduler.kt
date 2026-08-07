package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Обычные (регулярные) будильники: список, сериализация меток через Base64,
 * установка/отмена. Отделено от AlarmScheduler (п.3.3).
 */
class RegularAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PreferenceKeys.ALARM_PREFS, Context.MODE_PRIVATE)

    companion object {
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
            Log.e("RegularAlarmScheduler", "Ошибка установки обычного будильника", e)
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
}
