package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.salarynaftan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    primaryColor: Color,
    permissionManager: PermissionManager
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val scheduleViewModel = koinViewModel<ScheduleViewModel>()
    val scheduleState by scheduleViewModel.state.collectAsState()
    val visibleMonth = scheduleState.visibleMonth

    // ===== ИСПОЛЬЗУЕМ KOIN ДЛЯ ПОЛУЧЕНИЯ ЗАВИСИМОСТЕЙ =====
    val settingsManager = koinInject<SettingsManager>()
    val scheduler = koinInject<AlarmScheduler>()
    val colorSettings = koinInject<ColorSettingsManager>()
    val coroutineScope = rememberCoroutineScope()

    val viewingBrigade = scheduleState.viewingBrigade
    val viewingScheduleType = scheduleState.viewingScheduleType

    // Смена графика: переключаем доменный активный график, чтобы весь экран
    // (календарь, бригады, сегодня, итоги, экспорт) считал расписание заново.
    fun switchScheduleType(type: ScheduleType) = scheduleViewModel.switchScheduleType(type)

    fun loadMissedDays(month: YearMonth): Set<Int> {
        val key = "${month.year}-${month.monthValue}"
        return scheduleState.missedDays[key] ?: emptySet()
    }

    fun loadVacationDays(month: YearMonth): Set<Int> {
        val key = "${month.year}-${month.monthValue}"
        return scheduleState.vacationDays[key] ?: emptySet()
    }

    fun toggleMissedDay(day: Int, month: YearMonth) {
        scheduleViewModel.toggleMissedDay(day, month)
    }

    // ===== Отпуск: отдельное окно с датами «от» и «до» =====
    var showVacationDialog by remember { mutableStateOf(false) }
    var vacationFrom by remember { mutableStateOf(today) }
    var vacationTo by remember { mutableStateOf(today) }

    fun applyVacation(remove: Boolean) {
        scheduleViewModel.applyVacation(vacationFrom, vacationTo, remove)
        showVacationDialog = false
    }

    var exactAlarmsAllowed by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }

    var morningColor by remember { mutableStateOf(colorSettings.getMorningColor()) }
    var dayColor by remember { mutableStateOf(colorSettings.getDayColor()) }
    var nightColor by remember { mutableStateOf(colorSettings.getNightColor()) }
    var offColor by remember { mutableStateOf(colorSettings.getOffColor()) }

    // Material You: при переключении темы или динамических цветов
    // перечитываем актуальные цвета смен из DataStore (п.2.5).
    LaunchedEffect(isDarkTheme) {
        morningColor = colorSettings.getMorningColor()
        dayColor = colorSettings.getDayColor()
        nightColor = colorSettings.getNightColor()
        offColor = colorSettings.getOffColor()
    }

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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        PremiumHeader(
            title = stringResource(R.string.schedule_title),
            subtitle = stringResource(R.string.schedule_subtitle)
        )

        if (!exactAlarmsAllowed) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = DesignTokens.Danger.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, DesignTokens.Danger.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️ Точные будильники отключены", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignTokens.Danger, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Включить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Объединённая карточка: переключатель графика (№1/№2) + бригада
        // в одном PremiumSectionCard для компактности.
        PremiumSectionCard {
            Column {
                PremiumSectionTitle(icon = "🗓️", title = "График смен", subtitle = "Бригада $viewingBrigade · ${viewingScheduleType.displayName}")
                PremiumDivider()
                Spacer(modifier = Modifier.height(6.dp))
                // График №1/№2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) {
                    ScheduleType.entries.forEach { type ->
                        val selected = viewingScheduleType == type
                        Surface(
                            onClick = { switchScheduleType(type) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) primaryColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "График №${type.ordinal + 1}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PremiumDivider()
                Spacer(modifier = Modifier.height(6.dp))
                // Бригады
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) {
                    (1..viewingScheduleType.brigadeCount).forEach { num ->
                        val selected = viewingBrigade == num
                        Surface(
                            onClick = { scheduleViewModel.setViewingBrigade(num) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) primaryColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                num.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Блок сегодняшней смены — компактный
        TodayShiftCard(
            brigade = viewingBrigade,
            primaryColor = primaryColor,
            scheduleType = viewingScheduleType
        )

        // Календарь
        MonthCalendarPager(
            visibleMonth = visibleMonth,
            selectedBrigade = viewingBrigade,
            scheduleType = viewingScheduleType,
            onMonthChange = { scheduleViewModel.setVisibleMonth(it) },
            onGoToToday = { scheduleViewModel.setVisibleMonth(YearMonth.from(today)) },
            morningColor = morningColor,
            dayColor = dayColor,
            nightColor = nightColor,
            offColor = offColor,
            primaryColor = primaryColor,
            onExportClick = { showExportDialog = true },
            loadMissedDays = { month -> loadMissedDays(month) },
            loadVacationDays = { month -> loadVacationDays(month) },
            onDayClick = { day, month -> toggleMissedDay(day, month) }
        )

        // Кнопка управления отпуском (отдельное окно с датами от/до)
        Button(
            onClick = { showVacationDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("☀ Отпуск", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Синхронизация графика смен с системным календарём (п.3.1).
        fun syncWithCalendar(add: Boolean) {
            if (!permissionManager.hasCalendarPermission()) {
                permissionManager.requestCalendarPermission()
                return
            }
            coroutineScope.launch {
                val count = withContext(Dispatchers.IO) {
                    try {
                        if (add) {
                            when (val result = CalendarSyncCoordinator.syncMonth(
                                context, visibleMonth, viewingBrigade, viewingScheduleType
                            )) {
                                is CalendarSyncResult.Success -> result.added
                                CalendarSyncResult.Failed -> -1
                            }
                        } else {
                            CalendarSyncManager.removeMonthFromCalendar(
                                context, visibleMonth, viewingBrigade
                            )
                        }
                    } catch (_: Exception) {
                        -1
                    }
                }
                val msg = if (add) {
                    if (count >= 0) "Добавлено событий в календарь: $count"
                    else "Календарь недоступен"
                } else {
                    if (count > 0) "Удалено событий из календаря: $count"
                    else "Событий в календаре не найдено"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
        CalendarSyncButtons(
            primaryColor = primaryColor,
            onAdd = { syncWithCalendar(add = true) },
            onRemove = { syncWithCalendar(add = false) }
        )

        // Итоги месяца
        MonthlyStatsCard(
            visibleMonth = visibleMonth,
            selectedBrigade = viewingBrigade,
            primaryColor = primaryColor,
            missedDays = loadMissedDays(visibleMonth),
            vacationDays = loadVacationDays(visibleMonth),
            settingsManager = settingsManager
        )

        // Праздники месяца (с названиями)
        HolidaysCard(visibleMonth = visibleMonth, primaryColor = primaryColor)

        // Значки (легенда) — компактная справочная карточка
        ScheduleLegend()

        // Сворачиваемая справка «Как пользоваться графиком»
        ScheduleHelpBlock()

        // Карточка экспорта
        ExportSection(
            primaryColor = primaryColor,
            onExportClick = { showExportDialog = true }
        )
    }

    if (showExportDialog) {
        ScheduleExportDialog(
            month = visibleMonth,
            brigade = viewingBrigade,
            scheduleType = viewingScheduleType,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showVacationDialog) {
        VacationDialog(
            from = vacationFrom,
            to = vacationTo,
            onFromChange = { vacationFrom = it },
            onToChange = { vacationTo = it },
            onApply = { applyVacation(false) },
            onRemove = { applyVacation(true) },
            onDismiss = { showVacationDialog = false }
        )
    }
}
