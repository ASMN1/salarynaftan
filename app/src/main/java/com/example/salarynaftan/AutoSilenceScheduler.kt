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
 * Авто-тишина: установка/снятие точных будильников включения и выключения
 * беззвучного режима. Отделено от AlarmScheduler (п.3.3).
 */
class AutoSilenceScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
                Log.e("AutoSilenceScheduler", "Ошибка установки авто-тишины", e)
            }
        }
    }
}
