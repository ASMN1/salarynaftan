package com.example.salarynaftan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.salarynaftan.di.appModule
import com.example.salarynaftan.util.FileLogTree
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // В релизе DebugTree бесполезен (logcat недоступен пользователю),
            // поэтому пишем логи в файл для прод-диагностики крашей и ошибок.
            Timber.plant(FileLogTree(this))
        }
        installGlobalExceptionHandler()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        createAlarmNotificationChannel()
        // Прогрев DataStore (бригада, anchor-дата) уходит в фоновый поток:
        // иначе первый запуск блокирует Main через runBlocking и рискует ANR.
        warmUpSettingsInBackground()
    }

    /** Прогревает настройки (базовая дата цикла, бригада виджета) в фоне. */
    private fun warmUpSettingsInBackground() {
        Thread {
            try {
                syncBrigadeForWidget()
                syncShiftScheduleAnchor()
                syncScheduleType()
            } catch (_: Exception) {
                // Невалидные настройки не должны ронять приложение.
            }
        }.apply {
            name = "salarynaftan-warmup"
            isDaemon = true
            start()
        }
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

    // Подхватываем выбранный тип графика (№1/№2) в доменный объект ShiftSchedule,
    // чтобы все вызовы расписания использовали активный график с первого запуска.
    private fun syncScheduleType() {
        try {
            ShiftSchedule.currentScheduleType = SettingsManager(this).getScheduleType()
            ShiftSchedule.anchorDateGraph2 = java.time.LocalDate.of(2026, 8, 8)
        } catch (_: Exception) {
            // невалидные настройки — оставляем дефолт
        }
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