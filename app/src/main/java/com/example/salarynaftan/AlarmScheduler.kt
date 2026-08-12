package com.example.salarynaftan

import android.app.AlarmManager
import android.content.Context
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Фасад над специализированными планировщиками будильников (п.3.3).
 *
 * Раньше весь функционал — сменные будильники, обычные будильники, авто-тишина,
 * перепланирование после загрузки, тестовый будильник, работа с метками —
 * лежал в одном классе AlarmScheduler (~450 строк). Это делало его трудночитаемым
 * и рискованным для изменений: любая правка затрагивала все области сразу.
 *
 * Класс разбит по ответственности:
 *  - [ShiftAlarmScheduler] — сменные будильники (расписания, активная бригада)
 *  - [RegularAlarmScheduler] — обычные будильники и сериализация меток
 *  - [AutoSilenceScheduler] — авто-тишина
 *
 * AlarmScheduler остаётся тонким фасадом с прежним публичным API, поэтому
 * все места вызова (UI, receivers, DI, тесты) продолжают работать без изменений.
 */
class AlarmScheduler(
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager(context)
) {
    private val shiftScheduler = ShiftAlarmScheduler(context, settingsManager)
    private val regularScheduler = RegularAlarmScheduler(context)
    private val autoSilenceScheduler = AutoSilenceScheduler(context)

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PreferenceKeys.ALARM_PREFS, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(): Boolean {
        // Обёртка вынесена в фасад, чтобы receivers и UI не зависели от Android SDK напрямую.
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Тестовый будильник (№19 из UI/UX): ставит настоящий сигнал через указанные
     * секунды (по умолчанию 10), чтобы пользователь убедился, что звук, вибрация
     * и разрешения (exact alarm) работают. Вызывает тот же AlarmReceiver, что и
     * настоящий будильник. Возвращает false, если точные будильники запрещены.
     */
    fun scheduleTestAlarm(delaySeconds: Int = 10): Boolean {
        if (!canScheduleExactAlarms()) {
            return false
        }
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        val intent = android.content.Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", "🔔 Тест будильника")
            action = "com.example.salarynaftan.TEST_ALARM"
        }
        // Уникальный requestCode, чтобы не конфликтовать с реальными будильниками
        val requestCode = 100000 + (System.currentTimeMillis() % 90000).toInt()
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return true
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Ошибка установки тестового будильника")
            return false
        }
    }

    // ===== Сменные будильники (делегирование в ShiftAlarmScheduler) =====

    fun isAlarmScheduledForShift(type: ShiftType, brigade: Int): Boolean =
        shiftScheduler.isAlarmScheduledForShift(type, brigade)

    fun getAlarmTimesForShift(type: ShiftType, brigade: Int): List<String> =
        shiftScheduler.getAlarmTimesForShift(type, brigade)

    fun saveAlarmTimesForShift(type: ShiftType, times: List<String>, brigade: Int) =
        shiftScheduler.saveAlarmTimesForShift(type, times, brigade)

    fun scheduleAlarmsForShift(type: ShiftType, brigade: Int): Int =
        shiftScheduler.scheduleAlarmsForShift(type, brigade)

    fun scheduleSingleShiftAlarm(type: ShiftType, brigade: Int, index: Int, timeStr: String) =
        shiftScheduler.scheduleSingleShiftAlarm(type, brigade, index, timeStr)

    fun cancelAlarmsForShift(type: ShiftType, brigade: Int) =
        shiftScheduler.cancelAlarmsForShift(type, brigade)

    fun cancelAllShiftAlarmsAcrossAllBrigades() =
        shiftScheduler.cancelAllShiftAlarmsAcrossAllBrigades()

    fun rescheduleShiftAlarmAfterRing(shiftType: ShiftType, brigade: Int, index: Int, timeStr: String) =
        shiftScheduler.rescheduleShiftAlarmAfterRing(shiftType, brigade, index, timeStr)

    fun rescheduleShiftReminder(shiftType: ShiftType, brigade: Int, index: Int, timeStr: String) =
        shiftScheduler.rescheduleShiftReminder(shiftType, brigade, index, timeStr)

    fun rescheduleAllAlarmsForBrigade(brigade: Int) =
        shiftScheduler.rescheduleAllAlarmsForBrigade(brigade)

    fun switchActiveBrigade(newBrigade: Int) =
        shiftScheduler.switchActiveBrigade(newBrigade)

    // ===== Обычные будильники (делегирование в RegularAlarmScheduler) =====

    fun getRegularAlarms(): List<RegularAlarm> = regularScheduler.getRegularAlarms()

    fun saveRegularAlarms(alarms: List<RegularAlarm>) = regularScheduler.saveRegularAlarms(alarms)

    fun scheduleSingleRegularAlarm(alarm: RegularAlarm) = regularScheduler.scheduleSingleRegularAlarm(alarm)

    fun cancelSingleRegularAlarm(alarmId: Long) = regularScheduler.cancelSingleRegularAlarm(alarmId)

    // ===== Авто-тишина (делегирование в AutoSilenceScheduler) =====

    fun updateAutoSilenceAlarms(isEnabled: Boolean, startTime: String, endTime: String) =
        autoSilenceScheduler.updateAutoSilenceAlarms(isEnabled, startTime, endTime)

    fun rescheduleAllAfterBoot() {
        // Каждый будильник планируется в изолированном runCatching: исключение
        // на одном (например, на конкретном OEM) не должно останавливать
        // восстановление остальных будильников (BUG-003).
        fun <T> attempt(label: String, block: () -> T) {
            try {
                block()
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Ошибка восстановления будильника ($label)")
            }
        }

        attempt("отмена сменных") { shiftScheduler.cancelAllShiftAlarmsAcrossAllBrigades() }
        attempt("отмена обычных") {
            getRegularAlarms().forEach { regularScheduler.cancelSingleRegularAlarm(it.id) }
        }

        // Восстанавливаем будильники для ВСЕХ бригад текущего графика, у которых
        // они были включены. Ключи хранятся в формате "shift_alarm_<brigade>_<type>".
        val scheduleType = settingsManager.getScheduleType()
        for (b in 1..scheduleType.brigadeCount) {
            ShiftType.entries.forEach { type ->
                if (isAlarmScheduledForShift(type, b)) {
                    attempt("сменная $b/${type.name}") {
                        shiftScheduler.scheduleAlarmsForShift(type, b)
                    }
                }
            }
        }
        getRegularAlarms().forEach { alarm ->
            if (alarm.isEnabled) {
                attempt("обычный ${alarm.id}") {
                    regularScheduler.scheduleSingleRegularAlarm(alarm)
                }
            }
        }
        // Настройки авто-тишины — в DataStore (п.6.8), а не в SharedPreferences.
        if (settingsManager.getAutoSilenceEnabled()) {
            val start = settingsManager.getAutoSilenceStart()
            val end = settingsManager.getAutoSilenceEnd()
            attempt("авто-тишина") {
                autoSilenceScheduler.updateAutoSilenceAlarms(true, start, end)
            }
        }
    }
}
