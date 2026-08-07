package com.example.salarynaftan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "ShiftReminderReceiver"
private const val REMINDER_NOTIFY_BASE = 2_000_000

/**
 * Пред-напоминание о смене (п.6.7). Срабатывает за N минут до очередного
 * сигнала смены и показывает ненавязчивое уведомление, после чего
 * автоматически перепланируется на следующий подходящий день.
 */
class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleReceive(appContext, intent)
            } catch (e: Exception) {
                Timber.e(e, "Ошибка в ShiftReminderReceiver")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleReceive(context: Context, intent: Intent) {
        val shiftTypeName = intent.getStringExtra("shift_type_name") ?: return
        val brigade = intent.getIntExtra("brigade", 1)

        val shiftType = try {
            ShiftType.valueOf(shiftTypeName)
        } catch (_: Exception) {
            return
        }

        // Сначала перепланируем напоминание на следующий подходящий день,
        // затем показываем уведомление (очередность не важна, но reschedule
        // не должен прекратиться из-за ошибки с уведомлением).
        val reminderLead = SettingsManager(context).getShiftReminderMinutes()
        if (reminderLead > 0) {
            val index = intent.getIntExtra("alarm_index", 0)
            val timeStr = intent.getStringExtra("alarm_time") ?: ""
            try {
                ShiftAlarmScheduler(context).rescheduleShiftReminder(shiftType, brigade, index, timeStr)
            } catch (e: Exception) {
                Timber.e(e, "Не удалось перепланировать пред-напоминание")
            }
        }

        val time = intent.getStringExtra("alarm_time") ?: ""
        val notifyId = REMINDER_NOTIFY_BASE + brigade * 1000 + shiftType.ordinal * 100
        try {
            val builder = Notifications.info(
                context = context,
                title = "🔔 Скоро смена — $time",
                text = "Напоминаем: ${shiftType.displayName} смена (бригада $brigade) начнётся в $time.",
                notificationId = notifyId
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifyId, builder.build())
        } catch (e: Exception) {
            Timber.e(e, "Не удалось показать пред-напоминание")
        }
    }
}
