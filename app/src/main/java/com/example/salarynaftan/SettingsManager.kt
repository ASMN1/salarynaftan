package com.example.salarynaftan

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.salarynaftan.data.DataStoreManager
import com.example.salarynaftan.util.colorToArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    // Общий инстанс на Context: SettingsManager и ColorSettingsManager должны
    // разделять ОДИН кэш и writeScope, иначе гонка данных между кэшами (п.3.4).
    private val dataStore = DataStoreManager.getInstance(context)
    private val appContext = context.applicationContext

    @Volatile
    private var ringtoneCacheInitialized = false
    @Volatile
    private var cachedRingtoneUri: Uri? = null

    private val _isDarkTheme = MutableStateFlow(dataStore.isDarkTheme())
    val isDarkThemeFlow: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    private val _useDynamicColors = MutableStateFlow(dataStore.getUseDynamicColors())
    val useDynamicColorsFlow: StateFlow<Boolean> = _useDynamicColors.asStateFlow()
    private val _useOled = MutableStateFlow(dataStore.getUseOled())
    val useOledFlow: StateFlow<Boolean> = _useOled.asStateFlow()
    private val _brigade = MutableStateFlow(dataStore.getBrigade())
    val brigadeFlow: StateFlow<Int> = _brigade.asStateFlow()
    private val _scheduleType = MutableStateFlow(dataStore.getScheduleType())
    val scheduleTypeFlow: StateFlow<ScheduleType> = _scheduleType.asStateFlow()

    // ----- ГРОМКОСТЬ -----
    fun saveVolume(volume: Float) = dataStore.saveVolume(volume)

    fun getVolume(): Float = dataStore.getVolume()

    // ----- МЕЛОДИЯ -----
    fun saveRingtoneUri(uriString: String?) {
        val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        cachedRingtoneUri = uri
        ringtoneCacheInitialized = true
        dataStore.saveRingtoneUri(uriString)
    }

    /**
     * Возвращает сохранённую мелодию, но только если она реально доступна
     * (№14). Сохранённый URI может стать битым: рингтон удалили, URI из
     * бэкапа недоступен, или Android вернул нечитаемый URI. В этом случае
     * возвращаем null (== «По умолчанию») и очищаем битое значение, чтобы
     * оно не «висело» и не мешало повторно открыть пикер.
     */
    fun getRingtoneUri(): Uri? {
        if (ringtoneCacheInitialized) return cachedRingtoneUri
        val uriString = dataStore.getRingtoneUri() ?: return null
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            clearInvalidRingtone()
            null
        } ?: return null
        return try {
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            if (ringtone != null) {
                cachedRingtoneUri = uri
                ringtoneCacheInitialized = true
                uri
            } else {
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
        cachedRingtoneUri = null
        ringtoneCacheInitialized = true
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
    fun saveTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        dataStore.saveTheme(isDark)
    }

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
        _scheduleType.value = type
        // При переключении графика активная бригада может выйти за новый диапазон
        // (например, бригада 5 при переходе на 4-бригадный График №2) — приводим
        // её к валидному номеру, чтобы графики/будильники/ЗП считались корректно.
        val current = dataStore.getBrigade()
        if (!type.isValidBrigade(current)) {
            setBrigade(1)
        } else {
            // DataStore is the sole source of the active brigade.
            ShiftWidgetProvider.triggerUpdate(appContext)
        }
    }

    fun setBrigade(brigade: Int) {
        dataStore.setBrigade(brigade)
        _brigade.value = dataStore.getBrigade()
        // Compatibility mirror for pre-DataStore widget/configuration data.
        // Runtime readers use DataStore only; this mirror can be removed after
        // the legacy widget migration window.
        appContext.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(PreferenceKeys.BRIGADE_KEY, _brigade.value).apply()
        ShiftWidgetProvider.triggerUpdate(appContext)
        // Авто-тишина проверяет «отсыпной день» по активной бригаде: при её смене
        // пересчитываем/переустанавливаем таймеры, чтобы тишина не осталась
        // настроенной под старую бригаду (п.4 анализа). Настройки — в DataStore (п.6.8).
        if (getAutoSilenceEnabled()) {
            val start = getAutoSilenceStart()
            val end = getAutoSilenceEnd()
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

    fun saveUseDynamicColors(use: Boolean) {
        _useDynamicColors.value = use
        dataStore.saveUseDynamicColors(use)
    }

    // ----- OLED-РЕЖИМ (чисто чёрный фон для тёмной темы) -----
    fun getUseOled(): Boolean = dataStore.getUseOled()

    fun saveUseOled(use: Boolean) {
        _useOled.value = use
        dataStore.saveUseOled(use)
    }

    // ----- ППС (отчисления в пенсионный фонд, % от начислений) -----
    fun getPpsPercent(): Double = dataStore.getPpsPercent().toDouble()

    fun savePpsPercent(value: Double) = dataStore.savePpsPercent(value.toFloat())

    // ----- МАСШТАБ ИНТЕРФЕЙСА -----
    fun getUiScale(): Float = dataStore.getUiScale()

    fun saveUiScale(scale: Float) = dataStore.saveUiScale(scale)

    // ----- БАЗОВАЯ ДАТА ЦИКЛА СМЕН -----
    fun getAnchorDateIso(): String = dataStore.getAnchorDate()

    fun saveAnchorDateIso(isoDate: String) {
        // Валидируем дату до сохранения: невалидная строка не должна
        // ронять настройку крашем DateTimeParseException (п.6.1).
        val parsed = try {
            java.time.LocalDate.parse(isoDate)
        } catch (e: java.time.format.DateTimeParseException) {
            return
        }
        dataStore.saveAnchorDate(isoDate)
        // Синхронизируем доменный объект графика, чтобы изменение вступило в силу
        // без перезапуска приложения (№12).
        ShiftSchedule.anchorDate = parsed
    }

    // ----- МАСШТАБ ИНТЕРФЕЙСА -----
    // (методы выше)
    // ----- НАРАСТАНИЕ ГРОМКОСТИ -----
    fun getVolumeRampSec(): Int = dataStore.getVolumeRampSec()

    fun saveVolumeRampSec(sec: Int) = dataStore.saveVolumeRampSec(sec)

    // ----- ПРЕД-НАПОМИНАНИЕ О СМЕНЕ (мин до сигнала, 0 = выключено) -----
    fun getShiftReminderMinutes(): Int = dataStore.getShiftReminderMinutes()

    fun saveShiftReminderMinutes(minutes: Int) = dataStore.saveShiftReminderMinutes(minutes)

    // ----- АВТО-ТИШИНА (перенесена из SharedPreferences в DataStore, п.6.8) -----
    fun getAutoSilenceEnabled(): Boolean = dataStore.getAutoSilenceEnabled()

    fun saveAutoSilenceEnabled(isEnabled: Boolean) = dataStore.saveAutoSilenceEnabled(isEnabled)

    fun getAutoSilenceStart(): String = dataStore.getAutoSilenceStart()

    fun saveAutoSilenceStart(time: String) = dataStore.saveAutoSilenceStart(time)

    fun getAutoSilenceEnd(): String = dataStore.getAutoSilenceEnd()

    fun saveAutoSilenceEnd(time: String) = dataStore.saveAutoSilenceEnd(time)
}
