package com.example.salarynaftan

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.salarynaftan.data.DataStoreManager
import com.example.salarynaftan.util.colorToArgb

class ColorSettingsManager(context: Context) {
    // Общий инстанс на Context: SettingsManager и ColorSettingsManager должны
    // разделять ОДИН кэш и writeScope, иначе гонка данных между кэшами (п.3.4).
    private val dataStore = DataStoreManager.getInstance(context)

    fun getMorningColor(): Color = Color(dataStore.getMorningColor())
    fun getDayColor(): Color = Color(dataStore.getDayColor())
    fun getNightColor(): Color = Color(dataStore.getNightColor())
    fun getOffColor(): Color = Color(dataStore.getOffColor())

    fun saveMorningColor(color: Color) = dataStore.saveMorningColor(colorToArgb(color))
    fun saveDayColor(color: Color) = dataStore.saveDayColor(colorToArgb(color))
    fun saveNightColor(color: Color) = dataStore.saveNightColor(colorToArgb(color))
    fun saveOffColor(color: Color) = dataStore.saveOffColor(colorToArgb(color))

    fun resetToDefaults() {
        dataStore.saveMorningColor(ShiftType.MORNING.defaultColorArgb)
        dataStore.saveDayColor(ShiftType.DAY.defaultColorArgb)
        dataStore.saveNightColor(ShiftType.NIGHT.defaultColorArgb)
        dataStore.saveOffColor(ShiftType.OFF.defaultColorArgb)
    }
}