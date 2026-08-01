package com.example.salarynaftan

import androidx.compose.ui.graphics.Color
import java.time.LocalTime


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
    )
}