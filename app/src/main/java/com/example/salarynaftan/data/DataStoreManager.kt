package com.example.salarynaftan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.salarynaftan.ShiftType
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStoreManager — единая точка доступа к настройкам приложения.
 *
 * Реализация построена на типизированном кэше на каждый ключ: первое чтение
 * ключа загружает его значение из DataStore (один блокирующий read), все
 * последующие чтения идут из памяти. Это избавляет от десятков однотипных
 * getter-блоков «ensure(KEY) { cacheX = read(KEY, DEFAULT) }».
 *
 * Записи уходят в фоновый scope (limitedParallelism(1)), поэтому не блокируют
 * UI-поток и выполняются строго в порядке вызовов — без потери финальной
 * записи при быстрых последовательных сохранениях.
 */
class DataStoreManager(context: Context) {

    private val store = context.settingsDataStore

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Ключи, уже загруженные из DataStore (защищают от повторного блокирующего чтения).
    private val loadedKeys = mutableSetOf<Preferences.Key<*>>()

    // In-memory кэш: ключ → значение. Заменяет десятки @Volatile-полей.
    private val cache = mutableMapOf<Preferences.Key<*>, Any>()

    companion object {
        private val KEY_VOLUME = floatPreferencesKey("alarm_volume")
        private val KEY_RINGTONE_URI = stringPreferencesKey("alarm_ringtone_uri")
        private val KEY_IS_DARK = booleanPreferencesKey("is_dark_theme")
        private val KEY_PRIMARY_COLOR = intPreferencesKey("primary_color")
        private val KEY_BACKGROUND_COLOR = intPreferencesKey("background_color")
        private val KEY_SURFACE_COLOR = intPreferencesKey("surface_color")
        private val KEY_BRIGADE = intPreferencesKey("selected_brigade")
        private val KEY_SALARY = stringPreferencesKey("salary_oklad_double") // новый ключ: старый хранился как float
        private val KEY_PREMIUM_COEF = floatPreferencesKey("salary_premium_koef")
        private val KEY_STAZH_KOEF = floatPreferencesKey("salary_stazh_koef")
        private val KEY_SELECTED_MONTH = intPreferencesKey("selected_month_index")
        private val KEY_USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        private val KEY_PPS_PERCENT = floatPreferencesKey("pps_percent")
        private val KEY_MORNING_COLOR = intPreferencesKey("morning_color")
        private val KEY_DAY_COLOR = intPreferencesKey("day_color")
        private val KEY_NIGHT_COLOR = intPreferencesKey("night_color")
        private val KEY_OFF_COLOR = intPreferencesKey("off_color")
        private val KEY_UI_SCALE = floatPreferencesKey("ui_scale")
        private val KEY_VOLUME_RAMP_SEC = intPreferencesKey("volume_ramp_sec")
        private val KEY_ANCHOR_DATE = stringPreferencesKey("anchor_date")
        private val KEY_SHIFT_REMINDER_MINUTES = intPreferencesKey("shift_reminder_minutes")
        // Версия данных настроек: при изменении структуры ключей увеличиваем
        // SCHEMA_VERSION и добавляем соответствующий шаг миграции (№9).
        private val KEY_DATA_VERSION = intPreferencesKey("data_version")

        // Зарплата хранится строкой, чтобы не терять точность: Float 1607.93f
        // при конвертации в Double даёт 1607.9299316..., что искажает расчёт.
        private const val DEFAULT_SALARY = "1607.93"
        private const val DEFAULT_PREMIUM_COEF = 0.45f
        private const val DEFAULT_VOLUME = 0.7f
        private val DEFAULT_PRIMARY_COLOR = 0xFF00E676.toInt()
        private val DEFAULT_BACKGROUND_COLOR_DARK = 0xFF121212.toInt()
        private val DEFAULT_SURFACE_COLOR_DARK = 0xFF1E1E1E.toInt()
        private val DEFAULT_BACKGROUND_COLOR_LIGHT = 0xFFFFFFFF.toInt()
        private val DEFAULT_SURFACE_COLOR_LIGHT = 0xFFF5F5F5.toInt()
        private const val DEFAULT_BRIGADE = 1
        private const val DEFAULT_STAZH_KOEF = 0.25f
        private const val DEFAULT_SELECTED_MONTH = 5
        private const val DEFAULT_PPS_PERCENT = 6f
        private val DEFAULT_MORNING_COLOR = ShiftType.MORNING.defaultColorArgb
        private val DEFAULT_DAY_COLOR = ShiftType.DAY.defaultColorArgb
        private val DEFAULT_NIGHT_COLOR = ShiftType.NIGHT.defaultColorArgb
        private val DEFAULT_OFF_COLOR = ShiftType.OFF.defaultColorArgb
        private const val DEFAULT_UI_SCALE = 1f
        private const val MIN_UI_SCALE = 0.7f
        private const val MAX_UI_SCALE = 1.5f
        private const val DEFAULT_VOLUME_RAMP_SEC = 10
        private const val MIN_VOLUME_RAMP_SEC = 2
        private const val MAX_VOLUME_RAMP_SEC = 30
        private const val DEFAULT_ANCHOR_DATE = "2026-01-01"
        private const val DEFAULT_SHIFT_REMINDER_MINUTES = 0
        private const val MIN_SHIFT_REMINDER_MINUTES = 0
        private const val MAX_SHIFT_REMINDER_MINUTES = 180

        // Текущая версия схемы настроек. Начинаем с 2, так как первая версия
        // существовала без этого ключа; значение отсутствует → версия 1.
        const val SCHEMA_VERSION = 2
    }

    // ---- Volume ----
    fun getVolume(): Float = load(KEY_VOLUME, DEFAULT_VOLUME)
    fun saveVolume(v: Float) = save(KEY_VOLUME, v)

    // ---- Ringtone ----
    fun getRingtoneUri(): String? {
        val uri = load(KEY_RINGTONE_URI, "")
        return if (uri.isEmpty()) null else uri
    }
    fun saveRingtoneUri(uri: String?) = save(KEY_RINGTONE_URI, uri ?: "")

    // ---- Theme ----
    fun isDarkTheme(): Boolean = load(KEY_IS_DARK, true)
    fun saveTheme(isDark: Boolean) = save(KEY_IS_DARK, isDark)

    // ---- Primary color ----
    fun getPrimaryColor(): Int = load(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR)
    fun savePrimaryColor(c: Int) = save(KEY_PRIMARY_COLOR, c)

    // ---- Background color ----
    fun getBackgroundColor(): Int = load(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR_DARK)
    fun saveBackgroundColor(c: Int) = save(KEY_BACKGROUND_COLOR, c)

    // ---- Surface color ----
    fun getSurfaceColor(): Int = load(KEY_SURFACE_COLOR, DEFAULT_SURFACE_COLOR_DARK)
    fun saveSurfaceColor(c: Int) = save(KEY_SURFACE_COLOR, c)

    // ---- Brigade ----
    fun getBrigade(): Int = load(KEY_BRIGADE, DEFAULT_BRIGADE) { it.coerceIn(1, 5) }
    fun setBrigade(b: Int) = save(KEY_BRIGADE, b) { it.coerceIn(1, 5) }

    // ---- Salary ----
    fun getSalary(): Double =
        load(KEY_SALARY, DEFAULT_SALARY).toDoubleOrNull() ?: DEFAULT_SALARY.toDouble()
    fun saveSalary(v: Double) = save(KEY_SALARY, v.toString())

    // ---- Premium Coef ----
    fun getPremiumCoef(): Float = load(KEY_PREMIUM_COEF, DEFAULT_PREMIUM_COEF)
    fun savePremiumCoef(v: Float) = save(KEY_PREMIUM_COEF, v)

    // ---- Stazh Koef ----
    fun getStazhKoef(): Float = load(KEY_STAZH_KOEF, DEFAULT_STAZH_KOEF)
    fun saveStazhKoef(v: Float) = save(KEY_STAZH_KOEF, v)

    // ---- Selected Month ----
    fun getSelectedMonthIndex(): Int = load(KEY_SELECTED_MONTH, DEFAULT_SELECTED_MONTH) { it.coerceIn(0, 11) }
    fun saveSelectedMonthIndex(i: Int) = save(KEY_SELECTED_MONTH, i) { it.coerceIn(0, 11) }

    // ---- Dynamic Colors ----
    fun getUseDynamicColors(): Boolean = load(KEY_USE_DYNAMIC_COLORS, false)
    fun saveUseDynamicColors(use: Boolean) = save(KEY_USE_DYNAMIC_COLORS, use)

    // ---- PPS percent (отчисления в ППС, % от начислений) ----
    fun getPpsPercent(): Float = load(KEY_PPS_PERCENT, DEFAULT_PPS_PERCENT)
    fun savePpsPercent(p: Float) = save(KEY_PPS_PERCENT, p) { it.coerceIn(0f, 100f) }

    // ---- UI Scale (масштаб интерфейса) ----
    fun getUiScale(): Float = load(KEY_UI_SCALE, DEFAULT_UI_SCALE)
    fun saveUiScale(s: Float) = save(KEY_UI_SCALE, s) { it.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE) }

    // ---- Volume Ramp (нарастание громкости, сек) ----
    fun getVolumeRampSec(): Int = load(KEY_VOLUME_RAMP_SEC, DEFAULT_VOLUME_RAMP_SEC)
    fun saveVolumeRampSec(s: Int) = save(KEY_VOLUME_RAMP_SEC, s) { it.coerceIn(MIN_VOLUME_RAMP_SEC, MAX_VOLUME_RAMP_SEC) }

    // ---- Базовая дата цикла смен (anchor date) ----
    fun getAnchorDate(): String = load(KEY_ANCHOR_DATE, DEFAULT_ANCHOR_DATE)
    fun saveAnchorDate(isoDate: String) {
        val safe = isoDate.takeIf { it.isNotBlank() } ?: DEFAULT_ANCHOR_DATE
        save(KEY_ANCHOR_DATE, safe)
    }

    // ---- Пред-напоминание о смене (минут до сигнала, 0 = выключено) ----
    fun getShiftReminderMinutes(): Int = load(KEY_SHIFT_REMINDER_MINUTES, DEFAULT_SHIFT_REMINDER_MINUTES)
    fun saveShiftReminderMinutes(minutes: Int) =
        save(KEY_SHIFT_REMINDER_MINUTES, minutes) { it.coerceIn(MIN_SHIFT_REMINDER_MINUTES, MAX_SHIFT_REMINDER_MINUTES) }

    // ---- Morning color ----
    fun getMorningColor(): Int = load(KEY_MORNING_COLOR, DEFAULT_MORNING_COLOR)
    fun saveMorningColor(c: Int) = save(KEY_MORNING_COLOR, c)

    // ---- Day color ----
    fun getDayColor(): Int = load(KEY_DAY_COLOR, DEFAULT_DAY_COLOR)
    fun saveDayColor(c: Int) = save(KEY_DAY_COLOR, c)

    // ---- Night color ----
    fun getNightColor(): Int = load(KEY_NIGHT_COLOR, DEFAULT_NIGHT_COLOR)
    fun saveNightColor(c: Int) = save(KEY_NIGHT_COLOR, c)

    // ---- Off color ----
    fun getOffColor(): Int = load(KEY_OFF_COLOR, DEFAULT_OFF_COLOR)
    fun saveOffColor(c: Int) = save(KEY_OFF_COLOR, c)

    fun resetAllColors() {
        savePrimaryColor(DEFAULT_PRIMARY_COLOR)
        saveMorningColor(DEFAULT_MORNING_COLOR)
        saveDayColor(DEFAULT_DAY_COLOR)
        saveNightColor(DEFAULT_NIGHT_COLOR)
        saveOffColor(DEFAULT_OFF_COLOR)
    }

    fun resetBackgroundAndSurface(isDark: Boolean) {
        if (isDark) { saveBackgroundColor(DEFAULT_BACKGROUND_COLOR_DARK); saveSurfaceColor(DEFAULT_SURFACE_COLOR_DARK) }
        else { saveBackgroundColor(DEFAULT_BACKGROUND_COLOR_LIGHT); saveSurfaceColor(DEFAULT_SURFACE_COLOR_LIGHT) }
    }

    // ---- Вспомогательные методы ----

    // Миграция структуры настроек (№9). Вызывается один раз при первом
    // обращении к DataStoreManager. Каждый шаг добавляет/переносит данные
    // по ключу, затем поднимает data_version. Отсутствие ключа версии
    // трактуется как версия 1.
    @Synchronized
    fun upgradeIfNeeded() {
        val current = runBlocking(Dispatchers.IO) {
            store.data.map { it[KEY_DATA_VERSION] ?: 1 }.first()
        }
        if (current >= SCHEMA_VERSION) return
        // Шаг 1 -> 2: оклад теперь хранится строкой (salary_oklad_double),
        // а раньше был float под ключом salary_oklad. Если есть старое
        // float-значение, а нового строкового ещё нет — переносим.
        if (current < 2) {
            writeScope.launch {
                store.edit { prefs ->
                    val hasNew = prefs[KEY_SALARY] != null
                    if (!hasNew) {
                        val old = prefs[floatPreferencesKey("salary_oklad")]
                        if (old != null) {
                            prefs[KEY_SALARY] = old.toString()
                        }
                    }
                    prefs[KEY_DATA_VERSION] = 2
                }
            }
        }
    }

    /**
     * Типизированное чтение ключа: загружает значение в кэш ровно один раз
     * (блокирующий read только при первом обращении), затем возвращает его,
     * применяя необязательное ограничение (coerce).
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> load(key: Preferences.Key<T>, default: T, coerce: (T) -> T = { it }): T {
        synchronized(loadedKeys) {
            if (key !in loadedKeys) {
                val read = runBlocking(Dispatchers.IO) { store.data.map { it[key] ?: default }.first() }
                cache[key] = read as Any
                loadedKeys.add(key)
            }
            if (loadedKeys.isNotEmpty()) upgradeIfNeeded()
        }
        return coerce(cache[key] as T)
    }

    /**
     * Типизированная запись: обновляет кэш и уходит в фоновый scope
     * (не блокирует вызывающий поток). Необязательный coerce применяется
     * к значению как при сохранении, так и в кэше.
     */
    private fun <T> save(key: Preferences.Key<T>, value: T, coerce: (T) -> T = { it }) {
        val v = coerce(value)
        cache[key] = v as Any
        writeScope.launch { store.edit { it[key] = v } }
    }
}
