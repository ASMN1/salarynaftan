package com.example.salarynaftan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

/**
 * Работник WorkManager для ежедневного обновления виджета графика смен.
 *
 * Используется как надёжный резерв на случай, если точный будильник
 * (setExactAndAllowWhileIdle) заблокирован приложением или производителем
 * (Doze / Battery Saver на Android 12+). WorkManager гарантированно
 * выполнит PeriodicWorkRequest хотя бы раз за интервал (в идеале ежедневно).
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ShiftWidgetProvider.refreshSync(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Widget update worker failed")
            Result.retry()
        }
    }
}
