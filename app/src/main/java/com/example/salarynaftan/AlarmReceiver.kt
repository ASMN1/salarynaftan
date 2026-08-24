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
import androidx.core.content.ContextCompat
import timber.log.Timber
import com.example.salarynaftan.di.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    private val alarmScheduler: AlarmScheduler
        get() = AppDependencies.alarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        // WakeLock: не даёт процессору уснуть, пока запускается Activity.
        // Критичен на заблокированном экране — без него система может
        // прибить процесс до того, как fullScreenIntent отработает.
        // Таймаут согласован с лимитом goAsync() (10с для broadcast-приёмника),
        // чтобы wakeLock не «висел» дольше, чем живёт pendingResult.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or
                    PowerManager.ON_AFTER_RELEASE,
            "salarynaftan:alarm"
        ).apply {
            acquire(10_000L)
        }

        // Выполняем работу в корутине с жёстким таймаутом, не превышающим лимит
        // goAsync() (10с), и гарантированно вызываем pendingResult.finish() в
        // finally — иначе при медленной перепланировке OS убьёт приёмник раньше,
        // чем завершится работа (ANR).
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                withTimeout(8_000L) {
                    handleReceive(context, intent)
                }
            } catch (e: Exception) {
                Timber.e(e, "Ошибка в AlarmReceiver (или превышен лимит времени)")
            } finally {
                try { wakeLock.release() } catch (_: Exception) { }
                pendingResult.finish()
            }
        }
    }

    private fun handleReceive(context: Context, intent: Intent) {
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
            putExtra("notification_id", notificationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }

        val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (kgm.isKeyguardLocked) {
            // Экран ЗАБЛОКИРОВАН: единственный способ открыть Activity поверх —
            // уведомление с fullScreenIntent. Показываем его (оно запустит Activity).
            if (hasNotificationPermission) {
                val notification = Notifications.alarm(
                    context = context,
                    title = title,
                    notificationId = notificationId,
                    ringIntent = ringIntent
                ).build()
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
            } else {
                Timber.w("Будильник на заблокированном экране без разрешения POST_NOTIFICATIONS — не сработает")
            }
        } else {
            // Экран РАЗБЛОКИРОВАН: запускаем Activity напрямую, БЕЗ уведомления
            // в шторке (пользователь хочет просто будильник на весь экран).
            try {
                context.startActivity(ringIntent)
            } catch (e: Exception) {
                Timber.e(e, "startActivity не удался")
            }
        }

        // Перепланирование сменных будильников (инкапсулировано в AlarmScheduler)
        if (shiftTypeName != null && alarmTime.isNotEmpty()) {
            try {
                val shiftType = ShiftType.valueOf(shiftTypeName)
                alarmScheduler.rescheduleShiftAlarmAfterRing(
                    shiftType = shiftType,
                    brigade = brigade,
                    index = alarmIndex,
                    timeStr = alarmTime
                )
            } catch (e: Exception) {
                Timber.e(e, "Ошибка перепланирования")
            }
        }
    }
}