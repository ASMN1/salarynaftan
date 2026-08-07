package com.example.salarynaftan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val pendingResult = goAsync()
            val appContext = context.applicationContext

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {

                try {
                    val scheduler = AlarmScheduler(appContext)
                    // Отменяем все существующие будильники, чтобы избежать дублирования
                    scheduler.cancelAllShiftAlarmsAcrossAllBrigades()
                    scheduler.cancelAllRegularAlarms()
                    // Затем перепланируем по сохранённым настройкам
                    scheduler.rescheduleAllAfterBoot()

                } catch (e: Exception) {
                    Timber.e(e, "Ошибка перепланирования будильников после загрузки")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}