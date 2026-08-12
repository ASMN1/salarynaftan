package com.example.salarynaftan

import androidx.compose.ui.graphics.Color

/**
 * Единый источник дефолтных цветов темы (п.2.3).
 *
 * Раньше логика «какие цвета по умолчанию для тёмной/светлой темы» была
 * продублирована в трёх местах: SettingsViewModel.setTheme, resetAllColors
 * и MainActivity.updateTheme. Любая правка дефолта требовала синхронизации
 * вручную — риск расхождения. Теперь все дефолты собраны здесь.
 */
object ThemeDefaults {

    /** Основной (акцентный) цвет для темы. */
    fun primary(isDark: Boolean): Color =
        if (isDark) Color(0xFF00E676) else Color(0xFF00A859)

    /** Цвет фона для темы. */
    fun background(isDark: Boolean): Color =
        if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)

    /** Цвет поверхности (карточек) для темы. */
    fun surface(isDark: Boolean): Color =
        if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)

    /** Цвет смены «Утро» по умолчанию. */
    fun morning(): Color = Color(ShiftType.MORNING.defaultColorArgb)

    /** Цвет смены «День» по умолчанию. */
    fun day(): Color = Color(ShiftType.DAY.defaultColorArgb)

    /** Цвет смены «Ночь» по умолчанию. */
    fun night(): Color = Color(ShiftType.NIGHT.defaultColorArgb)

    /** Цвет смены «Выходной» по умолчанию. */
    fun off(): Color = Color(ShiftType.OFF.defaultColorArgb)
}