package com.example.salarynaftan

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.salarynaftan.data.DataStoreManager
import com.example.salarynaftan.util.colorToArgb

class SettingsManager(context: Context) {

    private val dataStore = DataStoreManager(context)
    private val appContext = context.applicationContext

    // ----- ГРОМКОСТЬ -----
    fun saveVolume(volume: Float) = dataStore.saveVolume(volume)

    fun getVolume(): Float = dataStore.getVolume()

    // ----- МЕЛОДИЯ -----
    fun saveRingtoneUri(uriString: String?) = dataStore.saveRingtoneUri(uriString)

    /**
     * Возвращает сохранённую мелодию, но только если она реально доступна
     * (№14). Сохранённый URI может стать битым: рингтон удалили, URI из
     * бэкапа недоступен, или Android вернул нечитаемый URI. В этом случае
     * возвращаем null (== «По умолчанию») и очищаем битое значение, чтобы
     * оно не «висело» и не мешало повторно открыть пикер.
     */
    fun getRingtoneUri(): Uri? {
        val uriString = dataStore.getRingtoneUri() ?: return null
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            clearInvalidRingtone()
            null
        } ?: return null
        return try {
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            if (ringtone != null) uri else {
                clearInvalidRingtone()
                null
            }
        } catch (e: Exception) {
            clearInvalidRingtone()
            null
        }
    }

    /** Очищает сохранённый URI мелодии (возврат к мелодии «По умолчанию»). */
    private fun clearInvalidRingtone() {
        dataStore.saveRingtoneUri(null)
    }

    fun getRingtoneName(): String {
        val uri = getRingtoneUri() ?: return "По умолчанию"
        return try {
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            ringtone?.getTitle(appContext) ?: "По умолчанию"
        } catch (e: Exception) {
            "По умолчанию"
        }
    }

    // ----- ТЕМА -----
    fun saveTheme(isDark: Boolean) = dataStore.saveTheme(isDark)

    fun isDarkTheme(): Boolean = dataStore.isDarkTheme()

    // ----- ОСНОВНОЙ ЦВЕТ -----
    fun getPrimaryColor(): Color = Color(dataStore.getPrimaryColor())

    fun savePrimaryColor(color: Color) = dataStore.savePrimaryColor(colorToArgb(color))

    // ----- ЦВЕТ ФОНА -----
    fun getBackgroundColor(): Color = Color(dataStore.getBackgroundColor())

    fun saveBackgroundColor(color: Color) = dataStore.saveBackgroundColor(colorToArgb(color))

    // ----- ЦВЕТ КАРТОЧЕК (SURFACE) -----
    fun getSurfaceColor(): Color = Color(dataStore.getSurfaceColor())

    fun saveSurfaceColor(color: Color) = dataStore.saveSurfaceColor(colorToArgb(color))

    // ----- БРИГАДА -----
    fun getBrigade(): Int = dataStore.getBrigade()

    /** Текущий тип графика (График №1 / График №2). */
    fun getScheduleType(): ScheduleType = dataStore.getScheduleType()

    fun setScheduleType(type: ScheduleType) {
        val old = dataStore.getScheduleType()
        if (old == type) return
        dataStore.saveScheduleType(type)
        ShiftSchedule.currentScheduleType = type
        // При переключении графика активная бригада может выйти за новый диапазон
        // (например, бригада 5 при переходе на 4-бригадный График №2) — приводим
        // её к валидному номеру, чтобы графики/будильники/ЗП считались корректно.
        val current = dataStore.getBrigade()
        if (!type.isValidBrigade(current)) {
            setBrigade(1)
        } else {
            // Иначе просто перерисовываем виджет и пересчитываем будильники.
            appContext.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putInt(PreferenceKeys.BRIGADE_KEY, current).apply()
            ShiftWidgetProvider.triggerUpdate(appContext)
        }
    }

    fun setBrigade(brigade: Int) {
        dataStore.setBrigade(brigade)
        // Виджет читает бригаду из SharedPreferences, а не из DataStore —
        // синхронизируем оба источника.
        appContext.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putInt(PreferenceKeys.BRIGADE_KEY, brigade).apply()
        ShiftWidgetProvider.triggerUpdate(appContext)
        // Авто-тишина проверяет «отсыпной день» по активной бригаде: при её смене
        // пересчитываем/переустанавливаем таймеры, чтобы тишина не осталась
        // настроенной под старую бригаду (п.4 анализа).
        val autoPrefs = appContext.getSharedPreferences(PreferenceKeys.AUTO_SILENCE_PREFS, android.content.Context.MODE_PRIVATE)
        if (autoPrefs.getBoolean(PreferenceKeys.AUTO_SILENCE_ENABLED, false)) {
            val start = autoPrefs.getString(PreferenceKeys.AUTO_SILENCE_START, "08:00") ?: "08:00"
            val end = autoPrefs.getString(PreferenceKeys.AUTO_SILENCE_END, "16:00") ?: "16:00"
            try {
                AlarmScheduler(appContext).updateAutoSilenceAlarms(true, start, end)
            } catch (_: Exception) {
                // Невалидное время/доступ — пропускаем, тишина просто сохранит прежние таймеры.
            }
        }
    }

    // ----- ОКЛАД -----
    fun getSalary(): Double = dataStore.getSalary()

    fun saveSalary(value: Double) = dataStore.saveSalary(value)

    // ----- КОЭФФИЦИЕНТ ПРЕМИИ -----
    fun getPremiumCoef(): Double = dataStore.getPremiumCoef().toDouble()

    fun savePremiumCoef(value: Double) = dataStore.savePremiumCoef(value.toFloat())

    // ----- КОЭФФИЦИЕНТ СТАЖА -----
    fun getStazhKoef(): Double = dataStore.getStazhKoef().toDouble()

    fun saveStazhKoef(value: Double) = dataStore.saveStazhKoef(value.toFloat())

    // ----- ВЫБРАННЫЙ МЕСЯЦ ЗАРПЛАТЫ -----
    fun getSelectedMonthIndex(): Int = dataStore.getSelectedMonthIndex()

    fun saveSelectedMonthIndex(index: Int) = dataStore.saveSelectedMonthIndex(index)

    // ----- ДИНАМИЧЕСКИЕ ЦВЕТА (MATERIAL YOU) -----
    fun getUseDynamicColors(): Boolean = dataStore.getUseDynamicColors()

    fun saveUseDynamicColors(use: Boolean) = dataStore.saveUseDynamicColors(use)

    // ----- ППС (отчисления в пенсионный фонд, % от начислений) -----
    fun getPpsPercent(): Double = dataStore.getPpsPercent().toDouble()

    fun savePpsPercent(value: Double) = dataStore.savePpsPercent(value.toFloat())

    // ----- МАСШТАБ ИНТЕРФЕЙСА -----
    fun getUiScale(): Float = dataStore.getUiScale()

    fun saveUiScale(scale: Float) = dataStore.saveUiScale(scale)

    // ----- БАЗОВАЯ ДАТА ЦИКЛА СМЕН -----
    fun getAnchorDateIso(): String = dataStore.getAnchorDate()

    fun saveAnchorDateIso(isoDate: String) {
        dataStore.saveAnchorDate(isoDate)
        // Синхронизируем доменный объект графика, чтобы изменение вступило в силу
        // без перезапуска приложения (№12).
        java.time.LocalDate.parse(isoDate).let { ShiftSchedule.anchorDate = it }
    }

    // ----- МАСШТАБ ИНТЕРФЕЙСА -----
    // (методы выше)
    // ----- НАРАСТАНИЕ ГРОМКОСТИ -----
    fun getVolumeRampSec(): Int = dataStore.getVolumeRampSec()

    fun saveVolumeRampSec(sec: Int) = dataStore.saveVolumeRampSec(sec)

    // ----- ПРЕД-НАПОМИНАНИЕ О СМЕНЕ (мин до сигнала, 0 = выключено) -----
    fun getShiftReminderMinutes(): Int = dataStore.getShiftReminderMinutes()

    fun saveShiftReminderMinutes(minutes: Int) = dataStore.saveShiftReminderMinutes(minutes)
}