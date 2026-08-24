package com.example.salarynaftan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import timber.log.Timber
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.example.salarynaftan.di.AppDependencies

private const val TAG = "SilentModeReceiver"

class SilentModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(15_000L) { handleReceive(appContext, intent) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val prefs = context.getSharedPreferences(PreferenceKeys.AUTO_SILENCE_PREFS, Context.MODE_PRIVATE)
        val action = intent.action

        val hasDndPermission = notificationManager?.isNotificationPolicyAccessGranted ?: true

        // 1. Автоматический перезапуск таймеров на следующий день
        // Настройки авто-тишины перенесены в DataStore (п.6.8), prefs остаётся
        // только для временного состояния (сохранённый interruption filter).
        val settings = AppDependencies.settingsManager
        val scheduleType = settings.getScheduleType()
        if (action == PreferenceKeys.ACTION_SILENT_ON || action == PreferenceKeys.ACTION_SILENT_OFF) {
            val isEnabled = settings.getAutoSilenceEnabled()
            val startTime = settings.getAutoSilenceStart()
            val endTime = settings.getAutoSilenceEnd()
            if (isEnabled) {
                AppDependencies.alarmScheduler.updateAutoSilenceAlarms(true, startTime, endTime)
            }
        }

        // 2. Умная проверка: включать ли тишину. Включаем после ЛЮБОЙ ночной смены
        // (вчера была NIGHT) — это покрывает и первую, и вторую ночь подряд, когда
        // человек спит днём между сменами и после прихода домой.
        // Раньше требовался ещё и выходной сегодня (OFF), что ломало сценарий
        // двух ночей подряд (после первой ночи идёт вторая, а не выходной).
        // В тестовом режиме (test_mode=true) проверка пропускается — пользователь
        // хочет проверить механизм тишины независимо от графика.
        val testMode = intent.getBooleanExtra("test_mode", false)
        val currentBrigade = settings.getBrigade()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val isOtsypnoy = testMode ||
                ShiftSchedule.shiftFor(yesterday, currentBrigade, scheduleType) == ShiftType.NIGHT

        try {
            if (action == PreferenceKeys.ACTION_SILENT_ON) {
                if (isOtsypnoy && hasDndPermission) {
                    val currentFilter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL

                    prefs.edit()
                        .putInt(PreferenceKeys.KEY_SAVED_INTERRUPTION_FILTER, currentFilter)
                        .putBoolean(PreferenceKeys.KEY_WAS_SILENCED_TODAY, true)
                        .apply()

                    // Включаем полную тишину (без звука и вибрации)
                    if (notificationManager != null) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    } else {
                        // Fallback для старых версий
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    }
                } else if (isOtsypnoy && !hasDndPermission) {
                    // Отсыпной день есть, но нет разрешения «Не беспокоить» —
                    // молча ничего делать нельзя: пользователь должен понять, почему тишина не сработала.
                    Timber.w("Авто-тишина не сработала: нет разрешения DND (отсыпной день после ночной смены)")
                    try {
                        val notif = Notifications.info(
                            context = context,
                            title = "Авто-тишина не сработала",
                            text = "Предоставьте разрешение «Не беспокоить», чтобы автоматическая тишина после ночной смены работала",
                            notificationId = 98765
                        ).build()
                        notificationManager?.notify(98765, notif)
                    } catch (e: Exception) {
                        Timber.e(e, "Не удалось показать подсказку об авто-тишине")
                    }
                }
            } else if (action == PreferenceKeys.ACTION_SILENT_OFF) {
                val wasSilenced = prefs.getBoolean(PreferenceKeys.KEY_WAS_SILENCED_TODAY, false)
                if (wasSilenced && hasDndPermission) {
                    val savedFilter = prefs.getInt(PreferenceKeys.KEY_SAVED_INTERRUPTION_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)

                    // Восстанавливаем предыдущий режим, только если текущий всё ещё "Без звука"
                    if (notificationManager != null) {
                        val currentFilter = notificationManager.currentInterruptionFilter
                        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                            notificationManager.setInterruptionFilter(savedFilter)
                        }
                    } else {
                        // Fallback для старых версий
                        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        }
                    }

                    prefs.edit().putBoolean(PreferenceKeys.KEY_WAS_SILENCED_TODAY, false).apply()
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Ошибка доступа при авто-тишине")
        } catch (e: Exception) {
            // Обработка прочих ошибок (п.6.2): например, setInterruptionFilter
            // может бросить IllegalStateException на некоторых OEM, если
            // NotificationManager в невалидном состоянии. Не роняем receiver.
            Timber.e(e, "Неожиданная ошибка в авто-тишине")
        }
    }
}