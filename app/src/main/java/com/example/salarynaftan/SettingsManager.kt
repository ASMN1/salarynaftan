package com.example.salarynaftan

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.Color

class SettingsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val DEFAULT_VOLUME = 0.7f

        // Значения по умолчанию (Int)
        private val DEFAULT_PRIMARY_COLOR = 0xFF00E676.toInt()
        private val DEFAULT_BACKGROUND_COLOR = 0xFF121212.toInt()
        private val DEFAULT_SURFACE_COLOR = 0xFF1E1E1E.toInt()
    }

    // ----- ГРОМКОСТЬ -----
    fun saveVolume(volume: Float) {
        prefs.edit().putFloat(PreferenceKeys.KEY_VOLUME, volume).apply()
    }

    fun getVolume(): Float {
        return prefs.getFloat(PreferenceKeys.KEY_VOLUME, DEFAULT_VOLUME)
    }

    // ----- МЕЛОДИЯ -----
    fun saveRingtoneUri(uriString: String?) {
        prefs.edit().putString(PreferenceKeys.KEY_RINGTONE_URI, uriString).apply()
    }

    fun getRingtoneUri(): Uri? {
        val uriString = prefs.getString(PreferenceKeys.KEY_RINGTONE_URI, null)
        return if (uriString != null) Uri.parse(uriString) else null
    }

    fun getRingtoneName(): String {
        val uri = getRingtoneUri() ?: return "По умолчанию"
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "По умолчанию"
        } catch (e: Exception) {
            "По умолчанию"
        }
    }

    // ----- ТЕМА -----
    fun saveTheme(isDark: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.KEY_IS_DARK, isDark).apply()
    }

    fun isDarkTheme(): Boolean {
        return prefs.getBoolean(PreferenceKeys.KEY_IS_DARK, true)
    }

    // ----- ОСНОВНОЙ ЦВЕТ -----
    fun getPrimaryColor(): Color {
        val colorInt = prefs.getInt(PreferenceKeys.KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR)
        return Color(colorInt)
    }

    fun savePrimaryColor(color: Color) {
        prefs.edit().putInt(PreferenceKeys.KEY_PRIMARY_COLOR, colorToArgb(color)).apply()
    }

    // ----- ЦВЕТ ФОНА -----
    fun getBackgroundColor(): Color {
        val colorInt = prefs.getInt(PreferenceKeys.KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
        return Color(colorInt)
    }

    fun saveBackgroundColor(color: Color) {
        prefs.edit().putInt(PreferenceKeys.KEY_BACKGROUND_COLOR, colorToArgb(color)).apply()
    }

    // ----- ЦВЕТ КАРТОЧЕК (SURFACE) -----
    fun getSurfaceColor(): Color {
        val colorInt = prefs.getInt(PreferenceKeys.KEY_SURFACE_COLOR, DEFAULT_SURFACE_COLOR)
        return Color(colorInt)
    }

    fun saveSurfaceColor(color: Color) {
        prefs.edit().putInt(PreferenceKeys.KEY_SURFACE_COLOR, colorToArgb(color)).apply()
    }

    // ----- БРИГАДА -----
    fun getBrigade(): Int {
        return prefs.getInt(PreferenceKeys.BRIGADE_KEY, 1).coerceIn(1, 5)
    }

    fun setBrigade(brigade: Int) {
        val safeBrigade = brigade.coerceIn(1, 5)
        prefs.edit().putInt(PreferenceKeys.BRIGADE_KEY, safeBrigade).apply()
        ShiftWidgetProvider.triggerUpdate(context)
    }

}