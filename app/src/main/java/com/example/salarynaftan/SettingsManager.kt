package com.example.salarynaftan

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

class SettingsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VOLUME = "alarm_volume"
        private const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
        private const val KEY_IS_DARK = "is_dark_theme"
        private const val DEFAULT_VOLUME = 0.7f // 70% по умолчанию
    }

    fun saveVolume(volume: Float) {
        prefs.edit().putFloat(KEY_VOLUME, volume).apply()
    }

    fun getVolume(): Float {
        return prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
    }

    fun saveRingtoneUri(uriString: String?) {
        prefs.edit().putString(KEY_RINGTONE_URI, uriString).apply()
    }

    fun getRingtoneUri(): Uri? {
        val uriString = prefs.getString(KEY_RINGTONE_URI, null)
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

    fun saveTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()
    }

    fun isDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_IS_DARK, true) // Теперь всё чётко!
    }

    private val BRIGADE_KEY = "selected_brigade"

    fun getBrigade(): Int {
        return prefs.getInt(BRIGADE_KEY, 1).coerceIn(1, 5)
    }

    fun setBrigade(brigade: Int) {
        val safeBrigade = brigade.coerceIn(1, 5)
        prefs.edit().putInt(BRIGADE_KEY, safeBrigade).apply()
    }
}