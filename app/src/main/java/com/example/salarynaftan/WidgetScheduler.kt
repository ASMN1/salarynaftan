package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Owns AlarmManager/WorkManager policy for widget refreshes. */
class WidgetScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll() {
        scheduleMidnight()
        schedulePeriodic()
    }

    fun cancelAll() {
        cancelMidnight()
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun scheduleImmediate() {
        // НАХОДКА-3: используем ОТДЕЛЬНОЕ имя для разовой задачи, иначе
        // ExistingWorkPolicy.REPLACE убивает периодический worker с тем же именем,
        // и schedulePeriodic() с KEEP не может его восстановить, пока одноразовая
        // задача не завершится (периодическое обновление виджета терялось).
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
        )
        // Периодический worker с собственным именем остаётся нетронутым.
        schedulePeriodic()
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleMidnight() {
        val pendingIntent = pendingIntent()
        // Кэшируем «сейчас» ОДИН раз и клонируем для модификации, чтобы исключить
        // гонку: если между двумя Calendar.getInstance() менялся день, сравнение
        // before() могло дать неверный результат.
        val now = Calendar.getInstance()
        val calendar = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
            }
        } catch (_: Exception) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
        }
    }

    private fun cancelMidnight() {
        val pendingIntent = pendingIntent()
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
            action = ShiftWidgetProvider.ACTION_MIDNIGHT_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "shift_widget_daily_update"
        // Отдельное имя для разового немедленного обновления — не конфликтует
        // с периодическим worker (НАХОДКА-3).
        private const val IMMEDIATE_WORK_NAME = "shift_widget_immediate_update"
        // Изолированный диапазон, не пересекающийся с BootReceiver (1001),
        // обычными будильниками (1_000_000+) и тестовыми (100_000+).
        private const val MIDNIGHT_REQUEST_CODE = 800_001
    }
}