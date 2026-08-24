package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.ZoneId
import timber.log.Timber

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
            val on = parseTime(startTime, LocalTime.of(8, 0))
            var startLdt = now.with(on).withSecond(0).withNano(0)
            if (startLdt.isBefore(now)) startLdt = startLdt.plusDays(1)

            val off = parseTime(endTime, LocalTime.of(16, 0))
            var endLdt = now.with(off).withSecond(0).withNano(0)
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
                } else {
                    Timber.w("Авто-тишина не установлена: нет разрешения на точные будильники")
                }
            } catch (e: Exception) {
                // Timber вместо Log: в релизе логи пишутся в файл (п.6.4),
                // единообразно с остальными receiver'ами.
                Timber.e(e, "Ошибка установки авто-тишины")
            }
        }
    }

    /**
     * Тестовый режим авто-тишины: включает беззвучный режим через [delaySeconds]
     * секунд и выключает через [delaySeconds] + [silenceDurationSeconds] секунд.
     * Использует test_mode=true, чтобы обойти проверку «отсыпного дня».
     * Возвращает false, если точные будильники запрещены.
     */
    fun scheduleTestSilence(delaySeconds: Int, silenceDurationSeconds: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val now = System.currentTimeMillis()
        val onMillis = now + delaySeconds * 1000L
        val offMillis = onMillis + silenceDurationSeconds * 1000L

        val intentOn = Intent(context, SilentModeReceiver::class.java).apply {
            action = PreferenceKeys.ACTION_SILENT_ON
            putExtra("test_mode", true)
        }
        val intentOff = Intent(context, SilentModeReceiver::class.java).apply {
            action = PreferenceKeys.ACTION_SILENT_OFF
            putExtra("test_mode", true)
        }
        // Отдельные requestCode, чтобы не конфликтовать с реальной авто-тишиной (90001/90002).
        val piOn = PendingIntent.getBroadcast(context, 91001, intentOn, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piOff = PendingIntent.getBroadcast(context, 91002, intentOff, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, onMillis, piOn)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, offMillis, piOff)
            return true
        } catch (e: Exception) {
            Timber.e(e, "Ошибка установки тестовой авто-тишины")
            return false
        }
    }

    private fun parseTime(value: String, fallback: LocalTime): LocalTime =
        try { LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("HH:mm")) }
        catch (_: DateTimeParseException) { fallback }
}
