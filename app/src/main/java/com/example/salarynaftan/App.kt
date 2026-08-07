package com.example.salarynaftan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.salarynaftan.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // В релизе — тоже пишем логи (без дублирования DebugTree), чтобы
        // потери данных/ошибки экспорта не «молчали» (п.6.1).
        else Timber.plant(Timber.DebugTree())
        installGlobalExceptionHandler()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        createAlarmNotificationChannel()
        syncBrigadeForWidget()
        syncShiftScheduleAnchor()
    }

    /**
     * Глобальный аварийный обработчик (№28): ловит любое необработанное
     * исключение на любом потоке (в т.ч. из корутин, дошедших до обработчика
     * потока) и пишет его в Timber до того, как приложение упадёт. Не перехватывает
     * сами краши, а добавляет диагностику — по логам видно, что и где упало.
     * Сохраняем оригинальный обработчик и делегируем ему, чтобы поведение
     * системы (аварийное закрытие) не менялось.
     */
    private fun installGlobalExceptionHandler() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "Необработанное исключение на потоке %s", thread.name)
            } catch (_: Exception) {
                // Логи уже не спасти — уступаем оригинальному обработчику
            }
            original?.uncaughtException(thread, throwable)
        }
    }

    // Подхватываем сохранённую базовую дату цикла смен (если задана) в
    // доменный объект ShiftSchedule. По умолчанию там уже 2026-01-01.
    private fun syncShiftScheduleAnchor() {
        try {
            val iso = SettingsManager(this).getAnchorDateIso()
            ShiftSchedule.anchorDate = java.time.LocalDate.parse(iso)
        } catch (_: Exception) {
            // невалидная дата в настройках — оставляем дефолтную базу
        }
    }

    // Виджет читает бригаду из SharedPreferences, а приложение — из DataStore.
    // Синхронизируем оба источника на старте, чтобы рассинхрон (например,
    // при первом запуске или обновлении) не приводил к неверной бригаде в виджете.
    private fun syncBrigadeForWidget() {
        val dataStoreBrigade = SettingsManager(this).getBrigade()
        getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(PreferenceKeys.BRIGADE_KEY, dataStoreBrigade).apply()
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