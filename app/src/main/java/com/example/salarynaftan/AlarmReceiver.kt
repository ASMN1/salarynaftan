package com.example.salarynaftan

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

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
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            }

            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val ringIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                putExtra("alarm_title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }

            if (hasNotificationPermission) {
                // fullScreenIntent в уведомлении — основной механизм для
                // запуска Activity поверх заблокированного экрана (единый стиль)
                val notification = Notifications.alarm(
                    context = context,
                    title = title,
                    notificationId = notificationId,
                    ringIntent = ringIntent
                ).build()

                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
            }

            // Резервный механизм: прямой вызов startActivity.
            // На заблокированном экране игнорируется ОС — там работает fullScreenIntent.
            // На разблокированном — гарантированный мгновенный запуск.
            // (Работает и без разрешения уведомлений.)
            val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!kgm.isKeyguardLocked) {
                try {
                    context.startActivity(ringIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "startActivity не удался: ${e.message}", e)
                }
            }

            // Если нет разрешения на уведомления — всё равно пробуем запустить
            // активность напрямую (хотя на заблокированном экране это не сработает).
            if (kgm.isKeyguardLocked && !hasNotificationPermission) {
                Log.w(TAG, "Будильник на заблокированном экране без разрешения POST_NOTIFICATIONS — не сработает")
            }

            // Перепланирование сменных будильников (инкапсулировано в AlarmScheduler)
            if (shiftTypeName != null && alarmTime.isNotEmpty()) {
                try {
                    val shiftType = ShiftType.valueOf(shiftTypeName)
                    AlarmScheduler(context).rescheduleShiftAlarmAfterRing(
                        shiftType = shiftType,
                        brigade = brigade,
                        index = alarmIndex,
                        timeStr = alarmTime
                    )
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