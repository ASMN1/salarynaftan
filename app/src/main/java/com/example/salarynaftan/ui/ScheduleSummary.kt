package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.util.Locale

// ==========================================
// ИТОГИ МЕСЯЦА (КРАСИВЫЙ БЛОК)
// ==========================================

@Composable
fun MonthlyStatsCard(
    visibleMonth: YearMonth,
    selectedBrigade: Int,
    primaryColor: Color,
    missedDays: Set<Int> = emptySet(),
    vacationDays: Set<Int> = emptySet(),
    settingsManager: SettingsManager
) {
    val monthIndex = visibleMonth.monthValue - 1
    val scheduleType = settingsManager.getScheduleType()
    val shiftHours = scheduleType.shiftHours
    val dayShiftBonus = scheduleType.dayShiftNightBonusHours
    val normVal = MonthlyNorms.norm(visibleMonth.year, monthIndex, scheduleType)

    // Единый источник подсчёта итогов месяца — SalaryCalculator.monthStats
    // (тот же, что использует расчёт зарплаты). DRY: обе карточки считают
    // рабочие смены/ночные часы/аванс одинаково.
    val stats = SalaryCalculator.monthStats(
        year = visibleMonth.year,
        monthIndex = monthIndex,
        brigade = selectedBrigade,
        missedDays = missedDays,
        vacationDays = vacationDays,
        scheduleType = scheduleType
    )
    val workDays = stats.workDaysInt
    val holidayHours = stats.holidayHours
    val nightCount = stats.nightCount
    val dayCount = stats.dayCount
    val morningCount = stats.morningCount
    val shiftsBefore15 = stats.advanceShifts.toInt()

    val factHours = workDays * shiftHours
    val nightHoursTotal = (nightCount * shiftHours) + (dayCount * dayShiftBonus)
    val overtimeHours = maxOf(0.0, factHours - normVal)
    val salary = settingsManager.getSalary()
    // Единая формула аванса из SalaryCalculator (как в расчёте зарплаты).
    val advanceAmount = SalaryCalculator.advanceAmount(salary, normVal, shiftsBefore15, shiftHours)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📊", fontSize = 17.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Итоги месяца",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (overtimeHours > 0) {
                    Surface(
                        color = DesignTokens.TaxBase.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "+${overtimeHours.toInt()} ч",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DesignTokens.TaxBase,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Первая строка: Норма / Факт / Праздничные
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = "Норма часов",
                    value = "${normVal.toInt()} ч",
                    color = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Факт часов",
                    value = "${factHours.toInt()} ч",
                    color = if (overtimeHours > 0) DesignTokens.TaxBase else primaryColor,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Праздничные",
                    value = "${holidayHours.toInt()} ч",
                    color = if (holidayHours > 0) DesignTokens.Holiday else DesignTokens.Neutral,
                    modifier = Modifier.weight(1f)
                )
            }

            // Вторая строка: Ночные часы / Аванс / Пропущено
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = "Ночные часы",
                    value = "${nightHoursTotal.toInt()} ч",
                    subLabel = "$nightCount ночн. + $dayCount дневн. смен",
                    color = DesignTokens.Night,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Аванс",
                    value = "≈ ${String.format(Locale.US, "%.0f", advanceAmount)} руб",
                    subLabel = "$shiftsBefore15 смен до 15-го",
                    color = DesignTokens.Advance,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = if (missedDays.isNotEmpty() || vacationDays.isNotEmpty()) "Пропущено" else "Выходных",
                    value = if (missedDays.isNotEmpty() || vacationDays.isNotEmpty())
                        "${(missedDays.size + vacationDays.size)} дн."
                    else "${visibleMonth.lengthOfMonth() - workDays} дн.",
                    subLabel = when {
                        missedDays.isNotEmpty() && vacationDays.isNotEmpty() ->
                            "${missedDays.size * shiftHours.toInt() + vacationDays.size * shiftHours.toInt()} ч минус"
                        missedDays.isNotEmpty() -> "${missedDays.size * shiftHours.toInt()} ч минус"
                        vacationDays.isNotEmpty() -> "отпуск ${vacationDays.size * shiftHours.toInt()} ч"
                        else -> "OFF смен"
                    },
                    color = if (missedDays.isNotEmpty()) DesignTokens.Danger
                    else if (vacationDays.isNotEmpty()) DesignTokens.Vacation
                    else DesignTokens.Neutral,
                    modifier = Modifier.weight(1f)
                )
            }

            // Индикатор прогресса
            val progressDouble = (factHours.toDouble() / normVal).coerceIn(0.0, 1.5)
            val targetProgress = progressDouble.toFloat()
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 600),
                label = "progress"
            )
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Выработка", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            animatedProgress >= 1.0f -> DesignTokens.Success
                            animatedProgress >= 0.8f -> DesignTokens.TaxBase
                            else -> DesignTokens.Danger
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        animatedProgress >= 1.0f -> DesignTokens.Success
                        animatedProgress >= 0.8f -> DesignTokens.TaxBase
                        else -> DesignTokens.Danger
                    },
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    subLabel: String? = null,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// ЛЕГЕНДА И СПРАВКА ГРАФИКА СМЕН
// ==========================================

/** Справочная карточка «Значки и цвета» — сворачиваемая, как «Как пользоваться графиком». */
@Composable
fun ScheduleLegend() {
    var expanded by remember { mutableStateOf(false) }
    PremiumSectionCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎨", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Значки и цвета",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PremiumDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    // Цвета смен
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegendItem("У", ShiftType.MORNING.color, "Утро", Modifier.weight(1f))
                        LegendItem("Д", ShiftType.DAY.color, "День", Modifier.weight(1f))
                        LegendItem("Н", ShiftType.NIGHT.color, "Ночь", Modifier.weight(1f))
                        LegendItem("В", ShiftType.OFF.color, "Выходной", Modifier.weight(1f))
                    }

                    // Пометки дат
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegendBadge("💰", DesignTokens.Salary, "ЗП · 10-е*", Modifier.weight(1f))
                        LegendBadge("💵", DesignTokens.Advance, "Аванс · 25-е*", Modifier.weight(1f))
                        LegendBadge("☀", DesignTokens.Vacation, "Отпуск", Modifier.weight(1f))
                        LegendBadge("✕", DesignTokens.Danger, "Невыход", Modifier.weight(1f))
                    }

                    // Сноска: если день выплаты выпадает на выходной, он сдвигается
                    // на ближайший предыдущий рабочий день (пятницу).
                    Text(
                        text = "* Если 10-е/25-е выпадают на выходной — сдвигается на пятницу.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/** Цветной квадрат с буквой смены + подпись. */
@Composable
private fun LegendItem(
    mark: String,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .background(color.copy(alpha = 0.85f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (color == ShiftType.NIGHT.color) Color.White else Color.Black
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Значок-эмодзи с подписью для пометок дат. */
@Composable
private fun LegendBadge(
    mark: String,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Сворачиваемая справка «Как пользоваться графиком». */
@Composable
fun ScheduleHelpBlock() {
    var expanded by remember { mutableStateOf(false) }
    PremiumSectionCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ℹ️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Как пользоваться графиком",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HelpRow("👆 Тап по дню", "Пометить день невыходом (✕). Повторный тап — снять.")
                    HelpRow("☀ Кнопка «Отпуск»", "Откройте окно, выберите даты «от» и «до», нажмите «Отметить» или «Снять».")
                    HelpRow("💰 💵", "Дни зарплаты (10-е) и аванса (25-е). Если день выпал на выходной, выплата сдвигается на пятницу.")
                    HelpRow("👥 Бригада", "Выберите номер бригады, чтобы посмотреть её график смен.")
                }
            }
        }
    }
}

@Composable
private fun HelpRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(icon, fontSize = 14.sp, modifier = Modifier.padding(top = 1.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            lineHeight = 17.sp
        )
    }
}

// ==========================================
// ПРАЗДНИКИ МЕСЯЦА (С НАЗВАНИЯМИ)
// ==========================================

@Composable
fun HolidaysCard(visibleMonth: YearMonth, primaryColor: Color) {
    val holidays = Holidays.holidaysInMonth(visibleMonth.year, visibleMonth.monthValue - 1)
    if (holidays.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DesignTokens.Holiday.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Праздники месяца",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            holidays.forEach { (day, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$day ${visibleMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale("ru"))}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DesignTokens.Holiday,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

