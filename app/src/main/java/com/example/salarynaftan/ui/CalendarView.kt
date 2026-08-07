package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarPager(
    visibleMonth: YearMonth,
    selectedBrigade: Int,
    onMonthChange: (YearMonth) -> Unit,
    onGoToToday: () -> Unit,
    morningColor: Color,
    dayColor: Color,
    nightColor: Color,
    offColor: Color,
    primaryColor: Color,
    onExportClick: () -> Unit,
    loadMissedDays: (YearMonth) -> Set<Int>,
    loadVacationDays: (YearMonth) -> Set<Int>,
    onDayClick: (Int, YearMonth) -> Unit,
    onVacationClick: (Int, YearMonth) -> Unit,
    rangeModeActive: Boolean,
    rangeRemoveActive: Boolean,
    onToggleRangeMode: () -> Unit,
    onToggleRangeRemove: () -> Unit,
    onRangeDateTap: (LocalDate) -> Unit,
    rangeStartDate: LocalDate?
) {
    val today = remember { LocalDate.now() }
    val baseMonth = remember { YearMonth.now() }
    val initialPage = 1200
    val totalPages = 2400 // фиксированное значение

    // Вычисляем целевую страницу, но обрезаем, чтобы не выйти за [0, totalPages-1]
    val currentPageTarget = remember(visibleMonth) {
        (initialPage + (visibleMonth.year - baseMonth.year) * 12 + (visibleMonth.monthValue - baseMonth.monthValue))
            .coerceIn(0, totalPages - 1)
    }

    val pagerState = rememberPagerState(
        initialPage = currentPageTarget,
        pageCount = { totalPages } // фиксировано
    )

    LaunchedEffect(pagerState.currentPage) {
        val newMonth = baseMonth.plusMonths((pagerState.currentPage - initialPage).toLong())
        if (newMonth != visibleMonth) {
            onMonthChange(newMonth)
        }
    }

    LaunchedEffect(currentPageTarget) {
        if (pagerState.currentPage != currentPageTarget) {
            pagerState.animateScrollToPage(currentPageTarget)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Кнопки: Сегодня и режим выделения отпуска
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onGoToToday,
                    shape = RoundedCornerShape(14.dp),
                    color = if (visibleMonth == YearMonth.now()) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    else Color(0xFF00E676),
                    contentColor = if (visibleMonth == YearMonth.now()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else Color.Black
                ) {
                    Text(
                        "Сегодня",
                        color = if (visibleMonth == YearMonth.now()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                Surface(
                    onClick = onToggleRangeRemove,
                    shape = RoundedCornerShape(14.dp),
                    color = if (rangeRemoveActive) Color(0xFFFF5252)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    contentColor = if (rangeRemoveActive) Color.Black
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ) {
                    Text(
                        if (rangeRemoveActive) "Снять: нажмите конец" else "Снять отпуск",
                        color = if (rangeRemoveActive) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                Surface(
                    onClick = onToggleRangeMode,
                    shape = RoundedCornerShape(14.dp),
                    color = if (rangeModeActive) Color(0xFF40C4FF)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    contentColor = if (rangeModeActive) Color.Black
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ) {
                    Text(
                        if (rangeModeActive) "Отпуск: нажмите конец" else "Выделить отпуск",
                        color = if (rangeModeActive) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumIconButton(
                    onClick = { onMonthChange(visibleMonth.minusMonths(1)) },
                    background = primaryColor.copy(alpha = 0.1f),
                    contentColor = primaryColor
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Предыдущий месяц",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Строка «Месяц Год» с возможностью выбора месяца и года
                var showMonthPicker by remember { mutableStateOf(false) }
                var showYearPicker by remember { mutableStateOf(false) }
                val monthName = MonthlyNorms.MONTH_NAMES_NOMINATIVE[visibleMonth.monthValue - 1]

                // Быстрый переход: месяц + год
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showMonthPicker = true }
                    )
                    Text(
                        text = visibleMonth.year.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.clickable { showYearPicker = true }
                    )
                }

                if (showMonthPicker) {
                    AlertDialog(
                        onDismissRequest = { showMonthPicker = false },
                        title = { Text("Выберите месяц", fontSize = 14.sp) },
                        text = {
                            val cols = 3
                            Column {
                                MonthlyNorms.MONTH_NAMES_NOMINATIVE.indices.chunked(cols).forEach { rowIndices ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        rowIndices.forEach { mIdx ->
                                            val isSelected = mIdx == visibleMonth.monthValue - 1
                                            Button(
                                                onClick = {
                                                    onMonthChange(
                                                        YearMonth.of(visibleMonth.year, mIdx + 1)
                                                    )
                                                    showMonthPicker = false
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) primaryColor else Color.DarkGray
                                                ),
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    MonthlyNorms.MONTH_NAMES_NOMINATIVE[mIdx].take(3),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.Black else Color.White
                                                )
                                            }
                                        }
                                        repeat(cols - rowIndices.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                if (showYearPicker) {
                    val currentYear = visibleMonth.year
                    val years = (currentYear - 5)..(currentYear + 5)
                    AlertDialog(
                        onDismissRequest = { showYearPicker = false },
                        title = { Text("Выберите год", fontSize = 14.sp) },
                        text = {
                            Column {
                                years.toList().chunked(3).forEach { rowYears ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        rowYears.forEach { year ->
                                            val isSelected = year == visibleMonth.year
                                            Button(
                                                onClick = {
                                                    onMonthChange(YearMonth.of(year, visibleMonth.monthValue))
                                                    showYearPicker = false
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) primaryColor else Color.DarkGray
                                                ),
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    year.toString(),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.Black else Color.White
                                                )
                                            }
                                        }
                                        repeat(3 - rowYears.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                PremiumIconButton(
                    onClick = { onMonthChange(visibleMonth.plusMonths(1)) },
                    background = primaryColor.copy(alpha = 0.1f),
                    contentColor = primaryColor
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Следующий месяц",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                key = { it }, // явное указание ключа (по умолчанию индекс, но для надёжности)
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val monthForPage = remember(page) {
                    baseMonth.plusMonths((page - initialPage).toLong())
                }
                MonthGrid(
                    month = monthForPage,
                    brigade = selectedBrigade,
                    today = today,
                    morningColor = morningColor,
                    dayColor = dayColor,
                    nightColor = nightColor,
                    offColor = offColor,
                    primaryColor = primaryColor,
                    missedDays = loadMissedDays(monthForPage),
                    vacationDays = loadVacationDays(monthForPage),
                    onDayClick = { day -> onDayClick(day, monthForPage) },
                    onVacationClick = { day -> onVacationClick(day, monthForPage) },
                    rangeModeActive = rangeModeActive || rangeRemoveActive,
                    isRangeRemoving = rangeRemoveActive,
                    onRangeDateTap = { day -> onRangeDateTap(monthForPage.atDay(day)) },
                    isRangeStart = rangeStartDate != null
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthGrid(
    month: YearMonth,
    brigade: Int,
    today: LocalDate,
    morningColor: Color,
    dayColor: Color,
    nightColor: Color,
    offColor: Color,
    primaryColor: Color,
    missedDays: Set<Int> = emptySet(),
    vacationDays: Set<Int> = emptySet(),
    onDayClick: (Int) -> Unit = {},
    onVacationClick: (Int) -> Unit = {},
    rangeModeActive: Boolean = false,
    isRangeRemoving: Boolean = false,
    onRangeDateTap: (Int) -> Unit = {},
    isRangeStart: Boolean = false
) {
    val hapticFeedback = LocalHapticFeedback.current
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    // Детали дня для всплывающей подсказки при долгом нажатии (Tooltip)
    var tooltipDate by remember { mutableStateOf<LocalDate?>(null) }
    val firstDayOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val emptySlotsBefore = firstDayOfMonth.dayOfWeek.value - 1
    val totalCells = emptySlotsBefore + daysInMonth
    val rows = (totalCells + 6) / 7

    // Вычисляем даты зарплаты и аванса (сдвигаем на пятницу если выпадает на выходные)
    fun payDate(day: Int): LocalDate {
        var d = month.atDay(day.coerceAtMost(daysInMonth))
        while (d.dayOfWeek.value > 5) d = d.minusDays(1) // Сб=6, Вс=7 → пятница
        return d
    }
    val salaryDate = payDate(10)
    val advanceDate = payDate(25)

    fun getColorForShift(shift: ShiftType): Color {
        return when (shift) {
            ShiftType.MORNING -> morningColor
            ShiftType.DAY -> dayColor
            ShiftType.NIGHT -> nightColor
            ShiftType.OFF -> offColor
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day == "Сб" || day == "Вс") Color(0xFFFF5252) else Color.Gray,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - emptySlotsBefore + 1

                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        val shift = ShiftSchedule.shiftFor(date, brigade)
                        val isToday = date == today
                        val color = getColorForShift(shift)
                        val isSalary = date == salaryDate
                        val isAdvance = date == advanceDate

                        val isMissed = dayNumber in missedDays
                        val isVacation = dayNumber in vacationDays
                        val isHoliday = Holidays.isHoliday(date)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .background(
                                    color = when {
                                        isMissed -> Color(0xFFFF5252).copy(alpha = 0.7f)
                                        isVacation -> Color(0xFF40C4FF).copy(alpha = 0.7f)
                                        isHoliday -> Color(0xFFE040FB).copy(alpha = 0.28f)
                                        isSalary -> Color(0xFFFFD600).copy(alpha = 0.25f)
                                        isAdvance -> Color(0xFF00BFA5).copy(alpha = 0.25f)
                                        rangeModeActive && isRangeStart -> Color(if (isRangeRemoving) 0xFFFF5252 else 0xFF40C4FF).copy(alpha = 0.35f)
                                        else -> color.copy(alpha = 0.85f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = when {
                                        isToday -> 2.dp
                                        isMissed || isVacation -> 1.5.dp
                                        isHoliday -> 1.5.dp
                                        isSalary || isAdvance -> 2.5.dp
                                        else -> 0.5.dp
                                    },
                                    color = when {
                                        isToday -> primaryColor
                                        isMissed -> Color(0xFFFF5252)
                                        isVacation -> Color(0xFF40C4FF)
                                        isHoliday -> Color(0xFFE040FB)
                                        isSalary -> Color(0xFFFFD600)
                                        isAdvance -> Color(0xFF00BFA5)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .semantics {
                                    // TalkBack: читает дату, смену и пометки дня вслух
                                    contentDescription = buildString {
                                        append(date.format(DateTimeFormatter.ofPattern("dd.MM")))
                                        append(", ")
                                        append(shift.displayName)
                                        if (isToday) append(", сегодня")
                                        if (isSalary) append(", зарплата")
                                        if (isAdvance) append(", аванс")
                                        if (isHoliday) append(", праздничный день")
                                        if (isVacation) append(", отпуск")
                                        if (isMissed) append(", невыход")
                                    }
                                }
                                .combinedClickable(
                                    enabled = true, // клики и отпуск работают и на выходных
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (rangeModeActive) {
                                            onRangeDateTap(dayNumber)
                                        } else {
                                            onDayClick(dayNumber)
                                        }
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        tooltipDate = date // подсказка с деталями дня и действиями
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Верхняя строка: маркеры отпуска/невыхода и значка ЗП/аванса,
                                // показываются ВМЕСТЕ, а не вместо типа смены.
                                val topMark = when {
                                    isMissed -> "✕"
                                    isVacation -> "☀"
                                    else -> null
                                }
                                // Значок праздника — поверх смены.
                                val holidayMark = if (isHoliday) "🎉" else null
                                // Значок зарплаты/аванса показываем поверх типа смены.
                                val payMark = when {
                                    isSalary -> "💰"
                                    isAdvance -> "💵"
                                    else -> null
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (topMark != null) {
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.5f, animationSpec = tween(200)),
                                            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.5f, animationSpec = tween(150))
                                        ) {
                                            Text(
                                                text = topMark,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (payMark != null) {
                                        Text(
                                            text = payMark,
                                            fontSize = 9.sp
                                        )
                                    }
                                    if (holidayMark != null) {
                                        Text(
                                            text = holidayMark,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "$dayNumber",
                                    fontSize = if (isToday) 10.sp else 9.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (shift == ShiftType.NIGHT) Color.White else Color.Black
                                )
                                // Подпись ЗП/АВ и тип смены показываются вместе
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = shift.shortName,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (shift == ShiftType.NIGHT) Color.White.copy(alpha = 0.7f) else Color.DarkGray
                                    )
                                    if (isSalary || isAdvance) {
                                        Text(
                                            text = if (isSalary) "ЗП" else "АВ",
                                            fontSize = 6.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSalary) Color(0xFFFFD600) else Color(0xFF00BFA5)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).padding(1.dp))
                    }
                }
            }
        }
    }

    // Всплывающая подсказка (tooltip) с деталями дня при долгом нажатии
    tooltipDate?.let { d ->
        val ttShift = ShiftSchedule.shiftFor(d, brigade)
        AlertDialog(
            onDismissRequest = { tooltipDate = null },
            title = {
                Text(
                    d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Смена:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        Text("${ttShift.displayName} (${ttShift.shortName})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (ttShift.startTime != null && ttShift.endTime != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Время:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                            Text("${ttShift.startTime} – ${ttShift.endTime}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (d == salaryDate) {
                        Text("💰 День зарплаты", fontSize = 12.sp, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                    }
                    if (d == advanceDate) {
                        Text("💵 День аванса", fontSize = 12.sp, color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold)
                    }
                    if (Holidays.isHoliday(d)) {
                        Text("🎉 Праздничный (нерабочий) день", fontSize = 12.sp, color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                    }
                    if (d.dayOfMonth in vacationDays) {
                        Text("☀ Отмечен отпуском", fontSize = 12.sp, color = Color(0xFF40C4FF), fontWeight = FontWeight.Bold)
                    }
                    if (d.dayOfMonth in missedDays) {
                        Text("✕ Отмечен невыходом", fontSize = 12.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tooltipDate = null }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
