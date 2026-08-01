package com.example.salarynaftan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.salarynaftan.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        createAlarmNotificationChannel()
    }

    private fun createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ALARM,
                "Срабатывание будильника",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Полноэкранное оповещение при срабатывании будильника"
                setBypassDnd(true)
                // Звук и вибрация отключены на канале — ими управляет AlarmRingingActivity
                setSound(null, null)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_channel_high"
    }
}