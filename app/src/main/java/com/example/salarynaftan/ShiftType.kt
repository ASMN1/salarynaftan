package com.example.salarynaftan

import androidx.compose.ui.graphics.Color
import com.example.salarynaftan.util.colorToArgb
import java.time.LocalTime

/**
 * Типы смен с жёстко определёнными дефолтными цветами.
 * Default-значения — единый источник для ColorSettingsManager/DataStoreManager.
 */
enum class ShiftType(
    val displayName: String,
    val shortName: String,
    val color: Color,
    val startTime: LocalTime?,
    val endTime: LocalTime?
) {
    OFF(
        "Выходной",
        "В",
        Color(0xFFF8EDF3),
        null,
        null
    ),

    DAY(
        "День",
        "Д",
        Color(0xFFA2D39C),
        LocalTime.of(16, 0),
        LocalTime.of(0, 0)
    ),

    MORNING(
        "Утро",
        "У",
        Color(0xFFFEE45B),
        LocalTime.of(8, 0),
        LocalTime.of(16, 0)
    ),

    NIGHT(
        "Ночь",
        "Н",
        Color(0xFF4F6D91),
        LocalTime.of(0, 0),
        LocalTime.of(8, 0)
    );

    /** ARGB-представление дефолтного цвета для хранения в DataStore/SharedPreferences. */
    val defaultColorArgb: Int
        get() = colorToArgb(color)

    /**
     * Момент окончания смены, назначенной на [date]. DAY (16:00–00:00) пересекает
     * полночь и заканчивается на следующий день в 00:00; NIGHT/MORNING — в тот же день.
     * Для OFF возвращает null.
     */
    fun endDateTime(date: java.time.LocalDate): java.time.LocalDateTime? {
        val s = startTime ?: return null
        val e = endTime ?: return null
        val crossesMidnight = e.isBefore(s) || e == s
        return java.time.LocalDateTime.of(
            if (crossesMidnight) date.plusDays(1) else date,
            e
        )
    }
}