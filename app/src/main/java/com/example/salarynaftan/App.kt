package com.example.salarynaftan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.salarynaftan.di.appModule
import com.example.salarynaftan.data.DataStoreManager
import com.example.salarynaftan.util.FileLogTree
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class App : Application() {
    /** Scope всего процесса приложения, принадлежащий конкретному Application. */
    internal val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // Резервный работник авто-тишины (п.6.7): если AlarmManager заблокирован
        // Doze/производителем, WorkManager перепланирует авто-тишину раз в сутки.
        scheduleAutoSilenceBackup()
    }

    /**
     * Планирует резервный ежедневный работник авто-тишины (п.6.7).
     *
     * WorkManager гарантированно выполнит PeriodicWorkRequest хотя бы раз за
     * интервал, в отличие от setExactAndAllowWhileIdle, который может быть
     * заблокирован Doze или OEM-оптимизациями. Работник идемпотентен: если
     * будильники авто-тишины уже стоят, он просто перезапишет их теми же
     * значениями.
     */
    private fun scheduleAutoSilenceBackup() {
        try {
            val request = PeriodicWorkRequestBuilder<AutoSilenceBackupWorker>(24, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "auto_silence_backup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Timber.e(e, "Не удалось запланировать резервный работник авто-тишины")
        }
    }

    /** Прогревает настройки (базовая дата цикла, бригада виджета) в фоне. */
    private fun warmUpSettingsInBackground() {
        applicationScope.launch {
            try {
                DataStoreManager.getInstance(this@App).warmUp()
                syncShiftScheduleAnchor()
            } catch (_: Exception) {
                // Невалидные настройки не должны ронять приложение.
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
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
            val iso = com.example.salarynaftan.di.AppDependencies.settingsManager.getAnchorDateIso()
            ShiftSchedule.anchorDate = java.time.LocalDate.parse(iso)
        } catch (_: Exception) {
            // невалидная дата в настройках — оставляем дефолтную базу
        }
    }

    private fun createAlarmNotificationChannel() {
        val alarmChannel = NotificationChannel(
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
        // Отдельный канал для пред-напоминаний о смене (п.6.7):
        // звук по умолчанию и вибрация, чтобы напоминание было заметным.
        val reminderChannel = NotificationChannel(
            CHANNEL_SHIFT_REMINDER,
            "Напоминание о смене",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомление за N минут до смены"
            setBypassDnd(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(alarmChannel)
        nm.createNotificationChannel(reminderChannel)
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_channel_high"
        const val CHANNEL_SHIFT_REMINDER = "shift_reminder_channel"
    }
}