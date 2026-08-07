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

class DataStoreManager(context: Context) {

    private val store = context.settingsDataStore

    // Пишущие операции уходят в фоновый scope — раньше они блокировали
    // вызывающий поток через runBlocking (в т.ч. Main) на каждом сохранении.
    // limitedParallelism(1) гарантирует последовательное выполнение записей
    // в порядке вызовов, устраняя гонку, когда быстрые сохранения приходили
    // в произвольном порядке и могли «перезаписать» более свежее значение
    // более старым (BUG: потеря последней записи).
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Каждый ключ инициализируется из DataStore ровно один раз.
    private val loadedKeys = mutableSetOf<Preferences.Key<*>>()

    // In-memory cache — avoids runBlocking on every read after first access
    @Volatile private var cacheVolume = DEFAULT_VOLUME
    @Volatile private var cacheRingtoneUri = ""
    @Volatile private var cacheIsDark = true
    @Volatile private var cachePrimaryColor = DEFAULT_PRIMARY_COLOR
    @Volatile private var cacheBackgroundColor = DEFAULT_BACKGROUND_COLOR_DARK
    @Volatile private var cacheSurfaceColor = DEFAULT_SURFACE_COLOR_DARK
    @Volatile private var cacheBrigade = DEFAULT_BRIGADE
    @Volatile private var cacheSalary = DEFAULT_SALARY
    @Volatile private var cachePremiumCoef = DEFAULT_PREMIUM_COEF
    @Volatile private var cacheStazhKoef = DEFAULT_STAZH_KOEF
    @Volatile private var cacheSelectedMonth = DEFAULT_SELECTED_MONTH
    @Volatile private var cacheUseDynamicColors = false
    @Volatile private var cachePpsPercent = DEFAULT_PPS_PERCENT
    @Volatile private var cacheMorningColor = DEFAULT_MORNING_COLOR
    @Volatile private var cacheDayColor = DEFAULT_DAY_COLOR
    @Volatile private var cacheNightColor = DEFAULT_NIGHT_COLOR
    @Volatile private var cacheOffColor = DEFAULT_OFF_COLOR
    @Volatile private var cacheUiScale = DEFAULT_UI_SCALE
    @Volatile private var cacheVolumeRampSec = DEFAULT_VOLUME_RAMP_SEC
    @Volatile private var cacheAnchorDate = DEFAULT_ANCHOR_DATE
    @Volatile private var cacheShiftReminderMinutes = DEFAULT_SHIFT_REMINDER_MINUTES

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
    fun getVolume(): Float {
        ensure(KEY_VOLUME) { cacheVolume = readFloat(KEY_VOLUME, DEFAULT_VOLUME) }
        return cacheVolume
    }
    fun saveVolume(v: Float) { cacheVolume = v; writeFloat(KEY_VOLUME, v) }

    // ---- Ringtone ----
    fun getRingtoneUri(): String? {
        ensure(KEY_RINGTONE_URI) { cacheRingtoneUri = readString(KEY_RINGTONE_URI, "") }
        return if (cacheRingtoneUri.isEmpty()) null else cacheRingtoneUri
    }
    fun saveRingtoneUri(uri: String?) { cacheRingtoneUri = uri ?: ""; writeString(KEY_RINGTONE_URI, uri ?: "") }

    // ---- Theme ----
    fun isDarkTheme(): Boolean {
        ensure(KEY_IS_DARK) { cacheIsDark = readBool(KEY_IS_DARK, true) }
        return cacheIsDark
    }
    fun saveTheme(isDark: Boolean) { cacheIsDark = isDark; writeBool(KEY_IS_DARK, isDark) }

    // ---- Primary color ----
    fun getPrimaryColor(): Int {
        ensure(KEY_PRIMARY_COLOR) { cachePrimaryColor = readInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR) }
        return cachePrimaryColor
    }
    fun savePrimaryColor(c: Int) { cachePrimaryColor = c; writeInt(KEY_PRIMARY_COLOR, c) }

    // ---- Background color ----
    fun getBackgroundColor(): Int {
        ensure(KEY_BACKGROUND_COLOR) { cacheBackgroundColor = readInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR_DARK) }
        return cacheBackgroundColor
    }
    fun saveBackgroundColor(c: Int) { cacheBackgroundColor = c; writeInt(KEY_BACKGROUND_COLOR, c) }

    // ---- Surface color ----
    fun getSurfaceColor(): Int {
        ensure(KEY_SURFACE_COLOR) { cacheSurfaceColor = readInt(KEY_SURFACE_COLOR, DEFAULT_SURFACE_COLOR_DARK) }
        return cacheSurfaceColor
    }
    fun saveSurfaceColor(c: Int) { cacheSurfaceColor = c; writeInt(KEY_SURFACE_COLOR, c) }

    // ---- Brigade ----
    fun getBrigade(): Int {
        ensure(KEY_BRIGADE) { cacheBrigade = readInt(KEY_BRIGADE, DEFAULT_BRIGADE).coerceIn(1, 5) }
        return cacheBrigade
    }
    fun setBrigade(b: Int) { val v = b.coerceIn(1, 5); cacheBrigade = v; writeInt(KEY_BRIGADE, v) }

    // ---- Salary ----
    fun getSalary(): Double {
        ensure(KEY_SALARY) { cacheSalary = readString(KEY_SALARY, DEFAULT_SALARY) }
        return cacheSalary.toDoubleOrNull() ?: DEFAULT_SALARY.toDouble()
    }
    fun saveSalary(v: Double) {
        val s = v.toString()
        cacheSalary = s
        writeString(KEY_SALARY, s)
    }

    // ---- Premium Coef ----
    fun getPremiumCoef(): Float {
        ensure(KEY_PREMIUM_COEF) { cachePremiumCoef = readFloat(KEY_PREMIUM_COEF, DEFAULT_PREMIUM_COEF) }
        return cachePremiumCoef
    }
    fun savePremiumCoef(v: Float) { cachePremiumCoef = v; writeFloat(KEY_PREMIUM_COEF, v) }

    // ---- Stazh Koef ----
    fun getStazhKoef(): Float {
        ensure(KEY_STAZH_KOEF) { cacheStazhKoef = readFloat(KEY_STAZH_KOEF, DEFAULT_STAZH_KOEF) }
        return cacheStazhKoef
    }
    fun saveStazhKoef(v: Float) { cacheStazhKoef = v; writeFloat(KEY_STAZH_KOEF, v) }

    // ---- Selected Month ----
    fun getSelectedMonthIndex(): Int {
        ensure(KEY_SELECTED_MONTH) { cacheSelectedMonth = readInt(KEY_SELECTED_MONTH, DEFAULT_SELECTED_MONTH).coerceIn(0, 11) }
        return cacheSelectedMonth
    }
    fun saveSelectedMonthIndex(i: Int) { val v = i.coerceIn(0, 11); cacheSelectedMonth = v; writeInt(KEY_SELECTED_MONTH, v) }

    // ---- Dynamic Colors ----
    fun getUseDynamicColors(): Boolean {
        ensure(KEY_USE_DYNAMIC_COLORS) { cacheUseDynamicColors = readBool(KEY_USE_DYNAMIC_COLORS, false) }
        return cacheUseDynamicColors
    }
    fun saveUseDynamicColors(use: Boolean) { cacheUseDynamicColors = use; writeBool(KEY_USE_DYNAMIC_COLORS, use) }

    // ---- PPS percent (отчисления в ППС, % от начислений) ----
    fun getPpsPercent(): Float {
        ensure(KEY_PPS_PERCENT) { cachePpsPercent = readFloat(KEY_PPS_PERCENT, DEFAULT_PPS_PERCENT) }
        return cachePpsPercent
    }
    fun savePpsPercent(p: Float) {
        val v = p.coerceIn(0f, 100f)
        cachePpsPercent = v
        writeFloat(KEY_PPS_PERCENT, v)
    }

    // ---- UI Scale (масштаб интерфейса) ----
    fun getUiScale(): Float {
        ensure(KEY_UI_SCALE) { cacheUiScale = readFloat(KEY_UI_SCALE, DEFAULT_UI_SCALE) }
        return cacheUiScale
    }
    fun saveUiScale(s: Float) {
        val v = s.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        cacheUiScale = v
        writeFloat(KEY_UI_SCALE, v)
    }

    // ---- Volume Ramp (нарастание громкости, сек) ----
    fun getVolumeRampSec(): Int {
        ensure(KEY_VOLUME_RAMP_SEC) { cacheVolumeRampSec = readInt(KEY_VOLUME_RAMP_SEC, DEFAULT_VOLUME_RAMP_SEC) }
        return cacheVolumeRampSec
    }
    fun saveVolumeRampSec(s: Int) {
        val v = s.coerceIn(MIN_VOLUME_RAMP_SEC, MAX_VOLUME_RAMP_SEC)
        cacheVolumeRampSec = v
        writeInt(KEY_VOLUME_RAMP_SEC, v)
    }

    // ---- Базовая дата цикла смен (anchor date) ----
    fun getAnchorDate(): String {
        ensure(KEY_ANCHOR_DATE) { cacheAnchorDate = readString(KEY_ANCHOR_DATE, DEFAULT_ANCHOR_DATE) }
        return cacheAnchorDate
    }
    fun saveAnchorDate(isoDate: String) {
        val safe = isoDate.takeIf { it.isNotBlank() } ?: DEFAULT_ANCHOR_DATE
        cacheAnchorDate = safe
        writeString(KEY_ANCHOR_DATE, safe)
    }

    // ---- Пред-напоминание о смене (минут до сигнала, 0 = выключено) ----
    fun getShiftReminderMinutes(): Int {
        ensure(KEY_SHIFT_REMINDER_MINUTES) { cacheShiftReminderMinutes = readInt(KEY_SHIFT_REMINDER_MINUTES, DEFAULT_SHIFT_REMINDER_MINUTES) }
        return cacheShiftReminderMinutes
    }
    fun saveShiftReminderMinutes(minutes: Int) {
        val v = minutes.coerceIn(MIN_SHIFT_REMINDER_MINUTES, MAX_SHIFT_REMINDER_MINUTES)
        cacheShiftReminderMinutes = v
        writeInt(KEY_SHIFT_REMINDER_MINUTES, v)
    }

    // ---- Morning color ----
    fun getMorningColor(): Int {
        ensure(KEY_MORNING_COLOR) { cacheMorningColor = readInt(KEY_MORNING_COLOR, DEFAULT_MORNING_COLOR) }
        return cacheMorningColor
    }
    fun saveMorningColor(c: Int) { cacheMorningColor = c; writeInt(KEY_MORNING_COLOR, c) }

    // ---- Day color ----
    fun getDayColor(): Int {
        ensure(KEY_DAY_COLOR) { cacheDayColor = readInt(KEY_DAY_COLOR, DEFAULT_DAY_COLOR) }
        return cacheDayColor
    }
    fun saveDayColor(c: Int) { cacheDayColor = c; writeInt(KEY_DAY_COLOR, c) }

    // ---- Night color ----
    fun getNightColor(): Int {
        ensure(KEY_NIGHT_COLOR) { cacheNightColor = readInt(KEY_NIGHT_COLOR, DEFAULT_NIGHT_COLOR) }
        return cacheNightColor
    }
    fun saveNightColor(c: Int) { cacheNightColor = c; writeInt(KEY_NIGHT_COLOR, c) }

    // ---- Off color ----
    fun getOffColor(): Int {
        ensure(KEY_OFF_COLOR) { cacheOffColor = readInt(KEY_OFF_COLOR, DEFAULT_OFF_COLOR) }
        return cacheOffColor
    }
    fun saveOffColor(c: Int) { cacheOffColor = c; writeInt(KEY_OFF_COLOR, c) }

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

    // Инициализирует кэш ключа один раз (блокирующее чтение только здесь).
    private fun ensure(key: Preferences.Key<*>, init: () -> Unit) {
        synchronized(loadedKeys) {
            if (key !in loadedKeys) {
                init()
                loadedKeys.add(key)
            }
            if (loadedKeys.isNotEmpty()) {
                upgradeIfNeeded()
            }
        }
    }

    private fun readString(key: Preferences.Key<String>, default: String): String =
        runBlocking(Dispatchers.IO) { store.data.map { it[key] ?: default }.first() }

    private fun readBool(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
        runBlocking(Dispatchers.IO) { store.data.map { it[key] ?: default }.first() }

    private fun readInt(key: Preferences.Key<Int>, default: Int): Int =
        runBlocking(Dispatchers.IO) { store.data.map { it[key] ?: default }.first() }

    private fun readFloat(key: Preferences.Key<Float>, default: Float): Float =
        runBlocking(Dispatchers.IO) { store.data.map { it[key] ?: default }.first() }

    // Все записи — в фоне, не блокируют вызывающий поток.
    private fun writeString(key: Preferences.Key<String>, value: String) {
        writeScope.launch { store.edit { it[key] = value } }
    }

    private fun writeBool(key: Preferences.Key<Boolean>, value: Boolean) {
        writeScope.launch { store.edit { it[key] = value } }
    }

    private fun writeInt(key: Preferences.Key<Int>, value: Int) {
        writeScope.launch { store.edit { it[key] = value } }
    }

    private fun writeFloat(key: Preferences.Key<Float>, value: Float) {
        writeScope.launch { store.edit { it[key] = value } }
    }
}
