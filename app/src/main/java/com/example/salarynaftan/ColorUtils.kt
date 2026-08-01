package com.example.salarynaftan

import androidx.compose.ui.graphics.Color

/**
 * Общий вспомогательный метод для конвертации Compose Color в ARGB Int
 * (для хранения в SharedPreferences).
 */
fun colorToArgb(color: Color): Int {
    return android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}
