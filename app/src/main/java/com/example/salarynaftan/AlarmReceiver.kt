package com.example.salarynaftan

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

private const val TAG = "AlarmReceiver"

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        // WakeLock: не даёт процессору уснуть, пока запускается Activity.
        // Критичен на заблокированном экране — без него система может
        // прибить процесс до того, как fullScreenIntent отработает.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or
                    PowerManager.ON_AFTER_RELEASE,
            "salarynaftan:alarm"
        ).apply {
            acquire(30_000L)
        }

        try {
            val title = intent.getStringExtra("alarm_title") ?: "Смена"
            val shiftTypeName = intent.getStringExtra("shift_type_name")
            val alarmIndex = intent.getIntExtra("alarm_index", -1)
            val brigade = intent.getIntExtra("brigade", 1)
            val alarmTime = intent.getStringExtra("alarm_time") ?: ""

            val notificationId = if (shiftTypeName != null && alarmIndex >= 0) {
                val typeOrdinal = try {
                    ShiftType.valueOf(shiftTypeName).ordinal
                } catch (_: Exception) { 0 }
                brigade * 10000 + typeOrdinal * 1000 + alarmIndex
            } else {
                System.currentTimeMillis().toInt()
            }

            val ringIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                putExtra("alarm_title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // fullScreenIntent в уведомлении — основной механизм для
            // запуска Activity поверх заблокированного экрана
            val notification = NotificationCompat.Builder(context, App.CHANNEL_ALARM)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Будильник: $title")
                .setContentText("Время просыпаться!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)

            // Резервный механизм: прямой вызов startActivity.
            // На заблокированном экране игнорируется ОС — там работает fullScreenIntent.
            // На разблокированном — гарантированный мгновенный запуск.
            val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!kgm.isKeyguardLocked) {
                try {
                    context.startActivity(ringIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "startActivity не удался: ${e.message}", e)
                }
            }

            // Перепланирование сменных будильников
            if (shiftTypeName != null && alarmTime.isNotEmpty()) {
                try {
                    val shiftType = ShiftType.valueOf(shiftTypeName)
                    val scheduler = AlarmScheduler(context, SettingsManager(context))

                    if (scheduler.isAlarmScheduledForShift(shiftType, brigade)) {
                        scheduler.scheduleSingleShiftAlarm(
                            type = shiftType,
                            brigade = brigade,
                            index = alarmIndex,
                            timeStr = alarmTime
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка перепланирования: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в AlarmReceiver: ${e.message}", e)
        } finally {
            try { wakeLock.release() } catch (_: Exception) { }
            pendingResult.finish()
        }
    }
}