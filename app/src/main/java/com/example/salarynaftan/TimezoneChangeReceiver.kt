package com.example.salarynaftan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Обработчик смены часового пояса (п.4.2).
 *
 * При смене часового пояса (или переходе на летнее/зимнее время) таймеры
 * авто-тишины, рассчитанные по старому смещению, срабатывают неверно.
 * Этот receiver пересчитывает их по новому часовому поясу, если авто-тишина
 * включена.
 */
class TimezoneChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        try {
            val request = OneTimeWorkRequestBuilder<TimezoneRescheduleWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "timezone_reschedule", ExistingWorkPolicy.REPLACE, request
            )
        } catch (e: Exception) {
            Timber.e(e, "Не удалось поставить работу перепланирования часового пояса")
        }
    }
}