package com.example.salarynaftan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

/** Performs alarm rescheduling outside BroadcastReceiver's short execution window. */
class TimezoneRescheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val settings = com.example.salarynaftan.di.AppDependencies.settingsManager
        val scheduler = com.example.salarynaftan.di.AppDependencies.alarmScheduler
        scheduler.rescheduleAllAfterBoot()
        if (settings.getAutoSilenceEnabled()) {
            scheduler.updateAutoSilenceAlarms(
                true,
                settings.getAutoSilenceStart(),
                settings.getAutoSilenceEnd()
            )
        }
        Timber.i("Будильники перепланированы после смены часового пояса")
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "Ошибка перепланирования после смены часового пояса")
        Result.retry()
    }
}