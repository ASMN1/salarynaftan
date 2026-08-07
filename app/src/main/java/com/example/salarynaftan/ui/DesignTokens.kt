package com.example.salarynaftan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Централизованные дизайн-токены (п.1.2).
 *
 * Семантические цвета, которые раньше были захардкожены (Color(0xFFFF5252),
 * Color(0xFF00E676) и т.п.) по всему UI. Собираем их в одном месте, чтобы:
 *  - цвета были согласованы между экранами (график, зарплата, настройки);
 *  - сменить оттенок можно было в одном файле, а не в десятках мест.
 *
 * Нейтральные цвета (черный/белый) вынесены сюда же, чтобы не повторять
 * Color.White / Color.Black в десятках мест.
 */
object DesignTokens {

    // ===== Семантические акценты =====

    /** Красный — ошибки, опасные действия, невыходы. */
    val Danger = Color(0xFFFF5252)

    /** Зелёный — успех, начисления, «к выплате». */
    val Success = Color(0xFF00E676)

    /** Янтарный — зарплата, предупреждения. */
    val Salary = Color(0xFFFFD600)

    /** Бирюзовый — аванс. */
    val Advance = Color(0xFF00BFA5)

    /** Голубой — отпуск. */
    val Vacation = Color(0xFF40C4FF)

    /** Фиолетовый — праздники. */
    val Holiday = Color(0xFFE040FB)

    /** Оранжевый — налоговая база. */
    val TaxBase = Color(0xFFFFA726)

    /** Голубой — ночные смены в сводке месяца. */
    val Night = Color(0xFF42A5F5)

    /** Нейтральный серый — неактивные/пустые значения. */
    val Neutral = Color.Gray

    // ===== Календарь =====

    /** Цвет подсветки сегодняшнего дня. */
    val Today = Color(0xFF00E676)

    /** Цвет выходных (Сб/Вс) в шапке календаря. */
    val Weekend = Color(0xFFFF5252)

    // ===== Диалоги =====

    val DialogShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

    // ===== Карточки =====

    /** Радиус скругления основных карточек. */
    val CardShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)

    /** Радиус скругления кнопок. */
    val ButtonShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
}
