package com.example.salarynaftan

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }

    // ===== ИСПОЛЬЗУЕМ KOIN ДЛЯ ПОЛУЧЕНИЯ ЗАВИСИМОСТЕЙ =====
    val settingsManager = koinInject<SettingsManager>()
    val scheduler = koinInject<AlarmScheduler>()
    val colorSettings = koinInject<ColorSettingsManager>()

    // Активная бригада из настроек
    val activeBrigade = remember { settingsManager.getBrigade() }

    // Бригада для просмотра (локальная)
    var viewingBrigade by remember { mutableStateOf(activeBrigade) }

    var exactAlarmsAllowed by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }

    var morningColor by remember { mutableStateOf(colorSettings.getMorningColor()) }
    var dayColor by remember { mutableStateOf(colorSettings.getDayColor()) }
    var nightColor by remember { mutableStateOf(colorSettings.getNightColor()) }
    var offColor by remember { mutableStateOf(colorSettings.getOffColor()) }

    var showExportDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAllowed = scheduler.canScheduleExactAlarms()
                morningColor = colorSettings.getMorningColor()
                dayColor = colorSettings.getDayColor()
                nightColor = colorSettings.getNightColor()
                offColor = colorSettings.getOffColor()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "📅 График смен",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )

        if (!exactAlarmsAllowed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252)),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️ Точные будильники отключены", fontSize = 11.sp, color = Color(0xFFFF5252))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Включить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Выбор бригады для просмотра
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.DarkGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("👥 Бригада", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..5).forEach { num ->
                        val selected = viewingBrigade == num
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .background(
                                    color = if (selected) primaryColor else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewingBrigade = num },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                num.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (selected) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Календарь
        MonthCalendarPager(
            visibleMonth = visibleMonth,
            selectedBrigade = viewingBrigade,
            onMonthChange = { visibleMonth = it },
            morningColor = morningColor,
            dayColor = dayColor,
            nightColor = nightColor,
            offColor = offColor,
            primaryColor = primaryColor,
            onExportClick = { showExportDialog = true }
        )

        // Итоги месяца (уменьшены)
        MonthlyStatsCard(
            visibleMonth = visibleMonth,
            selectedBrigade = viewingBrigade,
            primaryColor = primaryColor
        )

        // Карточка экспорта
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.DarkGray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📤 Экспорт",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("PDF / PNG", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }

    if (showExportDialog) {
        ScheduleExportDialog(
            month = visibleMonth,
            brigade = viewingBrigade,
            onDismiss = { showExportDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarPager(
    visibleMonth: YearMonth,
    selectedBrigade: Int,
    onMonthChange: (YearMonth) -> Unit,
    morningColor: Color,
    dayColor: Color,
    nightColor: Color,
    offColor: Color,
    primaryColor: Color,
    onExportClick: () -> Unit
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.DarkGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMonthChange(visibleMonth.minusMonths(1)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("◀", fontSize = 14.sp, color = primaryColor)
                }

                // Строка «Месяц Год» с возможностью выбора года
                var showYearPicker by remember { mutableStateOf(false) }
                val monthName = MonthlyNorms.MONTH_NAMES_NOMINATIVE[visibleMonth.monthValue - 1]
                Text(
                    text = "$monthName ${visibleMonth.year}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showYearPicker = true }
                )

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

                IconButton(
                    onClick = { onMonthChange(visibleMonth.plusMonths(1)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("▶", fontSize = 14.sp, color = primaryColor)
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
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
fun MonthGrid(
    month: YearMonth,
    brigade: Int,
    today: LocalDate,
    morningColor: Color,
    dayColor: Color,
    nightColor: Color,
    offColor: Color,
    primaryColor: Color
) {
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .background(
                                    color = when {
                                        isSalary -> Color(0xFFFFD600).copy(alpha = 0.25f)
                                        isAdvance -> Color(0xFF00BFA5).copy(alpha = 0.25f)
                                        else -> color.copy(alpha = 0.85f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = when {
                                        isToday -> 2.dp
                                        isSalary || isAdvance -> 2.5.dp
                                        else -> 0.5.dp
                                    },
                                    color = when {
                                        isToday -> primaryColor
                                        isSalary -> Color(0xFFFFD600)
                                        isAdvance -> Color(0xFF00BFA5)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    fontSize = if (isToday) 10.sp else 9.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (shift == ShiftType.NIGHT) Color.White else Color.Black
                                )
                                if (isSalary || isAdvance) {
                                    Text(
                                        text = if (isSalary) "💰" else "💵",
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = if (isSalary) "ЗП" else "АВ",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSalary) Color(0xFFFFD600) else Color(0xFF00BFA5)
                                    )
                                } else {
                                    Text(
                                        text = shift.shortName,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (shift == ShiftType.NIGHT) Color.White.copy(alpha = 0.7f) else Color.DarkGray
                                    )
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
}

// ==========================================
// ИТОГИ МЕСЯЦА (УМЕНЬШЕННАЯ КАРТОЧКА)
// ==========================================

@Composable
fun MonthlyStatsCard(
    visibleMonth: YearMonth,
    selectedBrigade: Int,
    primaryColor: Color
) {
    val monthIndex = visibleMonth.monthValue - 1
    val monthInfo = MonthlyNorms.list[monthIndex]
    val normVal = monthInfo.norm

    var workDays = 0.0
    var holidayHours = 0.0
    var nightCount = 0
    var dayCount = 0
    var morningCount = 0
    val fixedHolidays = listOf(
        java.time.MonthDay.of(1, 1),
        java.time.MonthDay.of(1, 7),
        java.time.MonthDay.of(3, 8),
        java.time.MonthDay.of(5, 1),
        java.time.MonthDay.of(5, 9),
        java.time.MonthDay.of(7, 3),
        java.time.MonthDay.of(11, 7),
        java.time.MonthDay.of(12, 25)
    )

    for (day in 1..visibleMonth.lengthOfMonth()) {
        val date = visibleMonth.atDay(day)
        val shift = ShiftSchedule.shiftFor(date, selectedBrigade)
        when (shift) {
            ShiftType.OFF -> {}
            else -> {
                workDays += 1.0
                if (fixedHolidays.contains(java.time.MonthDay.from(date))) {
                    holidayHours += 8.0
                }
                when (shift) {
                    ShiftType.NIGHT -> nightCount++
                    ShiftType.DAY -> dayCount++
                    ShiftType.MORNING -> morningCount++
                    else -> {}
                }
            }
        }
    }
    val factHours = workDays * 8.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, primaryColor),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "📊 Итоги",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactStatItem("Норма", "${normVal.toInt()} ч")
                CompactStatItem("Факт", "${factHours.toInt()} ч")
                CompactStatItem("Праздн.", "${holidayHours.toInt()} ч")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactStatItem("Ночные", nightCount.toString())
                CompactStatItem("Дневные", dayCount.toString())
                CompactStatItem("Утренние", morningCount.toString())
            }
        }
    }
}

@Composable
fun CompactStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 8.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}