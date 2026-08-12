package com.example.salarynaftan

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import com.example.salarynaftan.di.AppDependencies

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val pendingResult = goAsync()
            val appContext = context.applicationContext

            kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {

                try {
                    withTimeout(30_000L) {
                    val scheduler = AppDependencies.alarmScheduler
                    // rescheduleAllAfterBoot сам отменяет существующие будильники
                    // (чтобы избежать дублирования) и перепланирует по настройкам —
                    // отдельные cancel-вызовы здесь избыточны и убраны.
                    scheduler.rescheduleAllAfterBoot()

                    // Если точные будильники запрещены (Android 12+ без специального
                    // разрешения), сигналы, поставленные через setExactAndAllowWhileIdle,
                    // не сработают. Напоминаем пользователю дать разрешение, иначе
                    // расписание смен молча перестанет звонить после перезагрузки.
                    if (!scheduler.canScheduleExactAlarms()) {
                        val notifyId = 1001
                        val builder = Notifications.info(
                            context = appContext,
                            title = "Разрешите точные будильники",
                            text = "После перезагрузки системы точные будильники недоступны. " +
                                "Откройте настройки приложения и разрешите «Будильники и напоминания», " +
                                "иначе сигналы смен перестанут срабатывать.",
                            notificationId = notifyId
                        )
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(notifyId, builder.build())
                        Timber.i("Точные будильники запрещены — показано напоминание пользователю")
                    }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Ошибка перепланирования будильников после загрузки")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}