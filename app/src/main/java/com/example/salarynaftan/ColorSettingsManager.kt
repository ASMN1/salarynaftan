package com.example.salarynaftan

import android.content.Context
import androidx.compose.ui.graphics.Color

class ColorSettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences(PreferenceKeys.COLOR_SETTINGS_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MORNING = "color_morning"
        private const val KEY_DAY = "color_day"
        private const val KEY_NIGHT = "color_night"
        private const val KEY_OFF = "color_off"

        // Стандартные цвета – используем Color напрямую
        private val DEFAULT_MORNING = Color(0xFFFEE45B)
        private val DEFAULT_DAY = Color(0xFFA2D39C)
        private val DEFAULT_NIGHT = Color(0xFF4F6D91)
        private val DEFAULT_OFF = Color(0xFFF8EDF3)
    }

    fun getMorningColor(): Color = getColor(KEY_MORNING, DEFAULT_MORNING)
    fun getDayColor(): Color = getColor(KEY_DAY, DEFAULT_DAY)
    fun getNightColor(): Color = getColor(KEY_NIGHT, DEFAULT_NIGHT)
    fun getOffColor(): Color = getColor(KEY_OFF, DEFAULT_OFF)

    fun saveMorningColor(color: Color) = saveColor(KEY_MORNING, color)
    fun saveDayColor(color: Color) = saveColor(KEY_DAY, color)
    fun saveNightColor(color: Color) = saveColor(KEY_NIGHT, color)
    fun saveOffColor(color: Color) = saveColor(KEY_OFF, color)

    private fun getColor(key: String, default: Color): Color {
        val colorInt = prefs.getInt(key, colorToArgb(default))
        return Color(colorInt)
    }

    private fun saveColor(key: String, color: Color) {
        prefs.edit().putInt(key, colorToArgb(color)).apply()
    }

    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_MORNING)
            .remove(KEY_DAY)
            .remove(KEY_NIGHT)
            .remove(KEY_OFF)
            .apply()
    }

}