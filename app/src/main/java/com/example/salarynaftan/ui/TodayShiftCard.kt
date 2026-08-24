package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Блок «Сегодня» (№9 из UI/UX): показывает текущую смену выбранной бригады
 * и обратный отсчёт до конца смены. Обновляется каждую минуту.
 */
@Composable
fun TodayShiftCard(
    brigade: Int,
    primaryColor: Color,
    scheduleType: ScheduleType = ScheduleType.GRAPH_1
) {
    val today = remember { mutableStateOf(LocalDate.now()) }
    var shift by remember { mutableStateOf(ShiftSchedule.shiftFor(today.value, brigade, scheduleType)) }
    LaunchedEffect(brigade, scheduleType) {
        shift = ShiftSchedule.shiftFor(today.value, brigade, scheduleType)
    }

    // Настраиваемые цвета смен из ColorSettingsManager — те же, что в календаре
    val colorSettings = org.koin.compose.koinInject<ColorSettingsManager>()
    val shiftColor = when (shift) {
        ShiftType.MORNING -> colorSettings.getMorningColor()
        ShiftType.DAY -> colorSettings.getDayColor()
        ShiftType.NIGHT -> colorSettings.getNightColor()
        ShiftType.OFF -> colorSettings.getOffColor()
    }

    // Текущее время, обновляется раз в минуту для обратного отсчёта.
    // При переходе через полночь обновляем today.value и shift.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val newDate = now.toLocalDate()
            if (newDate != today.value) {
                today.value = newDate
                shift = ShiftSchedule.shiftFor(newDate, brigade, scheduleType)
            }
            delay(60_000)
        }
    }

    val endTime = ShiftSchedule.shiftEndDateTime(today.value, shift, scheduleType)
    val remaining = endTime?.let { java.time.Duration.between(now, it) }
    val isActive = endTime != null && remaining != null && !remaining.isNegative

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Цветной индикатор смены — тоньше и ниже
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(36.dp)
                    .background(shiftColor, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Сегодня",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = today.value.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (shift == ShiftType.OFF) "Выходной день" else "${shift.displayName} · Бр $brigade",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (shift == ShiftType.NIGHT) shiftColor else MaterialTheme.colorScheme.onSurface
                )
            }

            // Правый блок: до конца смены
            if (shift != ShiftType.OFF) {
                if (isActive) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "до конца",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = formatRemaining(remaining!!),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryColor
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "смена",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = ShiftSchedule.shiftStartTime(shift, scheduleType)?.let {
                                if (it.isAfter(LocalDateTime.now().toLocalTime())) "сегодня в $it"
                                else "завтра в $it"
                            } ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRemaining(d: java.time.Duration): String {
    val totalMinutes = d.toMinutes().coerceAtLeast(0)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}ч ${m}м" else "${m}м"
}
