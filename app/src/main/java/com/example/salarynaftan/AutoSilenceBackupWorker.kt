package com.example.salarynaftan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

/**
 * Резервный работник WorkManager для авто-тишины (п.6.7).
 *
 * AlarmManager (setExactAndAllowWhileIdle) может быть заблокирован Doze или
 * производителем (Xiaomi, Huawei и т.п.). WorkManager гарантированно выполнит
 * PeriodicWorkRequest хотя бы раз за интервал, поэтому этот работник
 * перепланирует авто-тишину, если она включена, но её будильники не были
 * установлены (например, после перезагрузки или сбоя планировщика).
 */
class AutoSilenceBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Настройки авто-тишины — в DataStore (п.6.8), а не в SharedPreferences.
            val settings = com.example.salarynaftan.di.AppDependencies.settingsManager
            val isEnabled = settings.getAutoSilenceEnabled()
            if (isEnabled) {
                val start = settings.getAutoSilenceStart()
                val end = settings.getAutoSilenceEnd()
                // Перепланирование идемпотентно: если будильники уже стоят,
                // AlarmManager просто перезапишет их теми же значениями.
                com.example.salarynaftan.di.AppDependencies.alarmScheduler
                    .updateAutoSilenceAlarms(true, start, end)
                Timber.d("Авто-тишина перепланирована резервным работником")
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Резервный работник авто-тишины не выполнился")
            Result.retry()
        }
    }
}