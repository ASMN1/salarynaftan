package com.example.salarynaftan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import java.time.LocalDate

class SilentModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val prefs = context.getSharedPreferences("auto_silence_prefs", Context.MODE_PRIVATE)

        val action = intent.action

        val hasDndPermission = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            notificationManager != null
        ) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }

        // 1. Автоматический перезапуск (зацикливание) таймеров на следующий день
        if (action == "com.example.salarynaftan.ACTION_SILENT_ON" || action == "com.example.salarynaftan.ACTION_SILENT_OFF") {
            val isEnabled = prefs.getBoolean("auto_silence_enabled", false)
            val startTime = prefs.getString("auto_silence_start", "08:00") ?: "08:00"
            val endTime = prefs.getString("auto_silence_end", "16:00") ?: "16:00"

            if (isEnabled) {
                AlarmScheduler(context).updateAutoSilenceAlarms(true, startTime, endTime)
            }
        }

        // 2. Умная проверка: является ли сегодняшний день ОТСЫПНЫМ для текущей бригады
        val currentBrigade = SettingsManager(context).getBrigade()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val isOtsypnoy = ShiftSchedule.shiftFor(today, currentBrigade) == ShiftType.OFF &&
                ShiftSchedule.shiftFor(yesterday, currentBrigade) == ShiftType.NIGHT

        try {
            if (action == "com.example.salarynaftan.ACTION_SILENT_ON") {
                // Включаем тишину ТОЛЬКО если сегодня отсыпной
                if (isOtsypnoy) {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    val currentRingerMode = audioManager.ringerMode

                    // Сохраняем настройки
                    prefs.edit()
                        .putInt("saved_volume", currentVolume)
                        .putInt("saved_ringer_mode", currentRingerMode)
                        .apply()

                    // Включаем режим "Не беспокоить" (Без звука)
                    if (hasDndPermission) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                    }
                }
            } else if (action == "com.example.salarynaftan.ACTION_SILENT_OFF") {
                // Возвращаем звук
                val savedVolume = prefs.getInt("saved_volume", -1)
                val savedRingerMode = prefs.getInt("saved_ringer_mode", AudioManager.RINGER_MODE_NORMAL)

                if (hasDndPermission) {
                    // Возвращаем режим только если он всё ещё "Без звука" (пользователь сам его не менял)
                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                        audioManager.ringerMode = savedRingerMode
                    }
                }

                if (savedVolume != -1 && audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, savedVolume, 0)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}