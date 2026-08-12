package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber
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
        // Диапазон requestCode обычных будильников изолирован от сменных
        // (brigade*1000 + type*100 + index, максимум ~500 000) и тестового
        // (100 000–199 000), чтобы PendingIntent с одинаковым кодом не
        // «съедал» чужой будильник (п.2.1). Смещение 1_000_000 гарантирует
        // отсутствие пересечения диапазонов.
        private const val REQUEST_CODE_BASE = 1_000_000
        private const val REQUEST_CODE_RANGE = 900_000
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

        // Валидация диапазона: withHour(25)/withMinute(99) бросит
        // DateTimeException и уронит планировщик (п.6.2).
        if (hour !in 0..23 || minute !in 0..59) {
            Timber.w("Некорректное время будильника: ${alarm.time} — пропускаем")
            return
        }

        val now = LocalDateTime.now()
        var targetTime = now.withHour(hour).withMinute(minute).withSecond(0)
        if (targetTime.isBefore(now)) {
            targetTime = targetTime.plusDays(1)
        }

        val triggerMillis = targetTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", alarm.label)
        }

        // requestCode в изолированном диапазоне (п.2.1): не пересекается
        // со сменными/тестовыми будильниками.
        val requestCode = requestCodeFor(alarm.id)
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
            } else {
                Timber.w("Обычный будильник не запланирован (нет разрешения exact alarm)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Ошибка установки обычного будильника")
        }
    }

    fun cancelSingleRegularAlarm(alarmId: Long) {
        // Тот же изолированный диапазон, что и при установке (п.2.1).
        val requestCode = requestCodeFor(alarmId)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** Stable, collision-resistant mapping for persisted IDs; unlike modulo it
     * does not make IDs separated by 900000 share a PendingIntent. */
    @Synchronized
    private fun requestCodeFor(id: Long): Int {
        val key = "regular_alarm_request_code_$id"
        prefs.getInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { return it }
        var value = id xor (id ushr 32)
        value = value * -7046029254386353131L
        var code = REQUEST_CODE_BASE + Math.floorMod((value xor (value ushr 32)).toInt(), REQUEST_CODE_RANGE)
        val occupied = prefs.all.entries
            .filter { it.key.startsWith("regular_alarm_request_code_") }
            .mapNotNull { it.value as? Int }
            .toSet()
        while (code in occupied) {
            code = REQUEST_CODE_BASE + Math.floorMod(code - REQUEST_CODE_BASE + 1, REQUEST_CODE_RANGE)
        }
        prefs.edit().putInt(key, code).apply()
        return code
    }
}
