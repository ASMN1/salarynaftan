package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.salarynaftan.data.SalaryRepository
import kotlinx.coroutines.launch
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
    val salaryRepository = koinInject<SalaryRepository>()
    val coroutineScope = rememberCoroutineScope()

    // Активная бригада из настроек
    val activeBrigade = settingsManager.getBrigade()

    // Бригада для просмотра (локальная)
    var viewingBrigade by remember { mutableStateOf(activeBrigade) }

    // Пропущенные дни по месяцам: ключ = "год-месяц", значение = Set<Int>
    var missedDaysMap by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }

    // Отпускные дни по месяцам: ключ = "год-месяц", значение = Set<Int>
    var vacationDaysMap by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }

    fun loadMissedDays(month: YearMonth): Set<Int> {
        val key = "${month.year}-${month.monthValue}"
        return missedDaysMap[key] ?: emptySet()
    }

    fun loadVacationDays(month: YearMonth): Set<Int> {
        val key = "${month.year}-${month.monthValue}"
        return vacationDaysMap[key] ?: emptySet()
    }

    // Загружаем пропуски и отпуска из Room для текущего месяца при первом рендере
    LaunchedEffect(visibleMonth) {
        val monthIndex = visibleMonth.monthValue - 1
        val key = "${visibleMonth.year}-${visibleMonth.monthValue}"
        val days = salaryRepository.getMissedDays(visibleMonth.year, monthIndex)
        missedDaysMap = missedDaysMap + (key to days)
        val vac = salaryRepository.getVacationDays(visibleMonth.year, monthIndex)
        vacationDaysMap = vacationDaysMap + (key to vac)
    }

    fun toggleMissedDay(day: Int, month: YearMonth) {
        val key = "${month.year}-${month.monthValue}"
        val current = loadMissedDays(month).toMutableSet()
        if (day in current) current.remove(day) else current.add(day)
        missedDaysMap = missedDaysMap + (key to current)
        coroutineScope.launch {
            salaryRepository.saveMissedDays(month.year, month.monthValue - 1, current)
        }
    }

    // ===== Отпуск: отдельное окно с датами «от» и «до» =====
    var showVacationDialog by remember { mutableStateOf(false) }
    var vacationFrom by remember { mutableStateOf(today) }
    var vacationTo by remember { mutableStateOf(today) }

    fun applyVacation(remove: Boolean) {
        val begin = if (vacationFrom.isBefore(vacationTo)) vacationFrom else vacationTo
        val finish = if (vacationFrom.isBefore(vacationTo)) vacationTo else vacationFrom
        val toProcess = mutableMapOf<String, MutableSet<Int>>()
        var d = begin
        while (!d.isAfter(finish)) {
            val key = "${d.year}-${d.monthValue}"
            toProcess.getOrPut(key) { mutableSetOf() }.add(d.dayOfMonth)
            d = d.plusDays(1)
        }
        toProcess.forEach { (key, days) ->
            val y = key.substringBefore("-").toInt()
            val m = key.substringAfter("-").toInt()
            coroutineScope.launch {
                val current = salaryRepository.getVacationDays(y, m - 1).toMutableSet()
                if (remove) current.removeAll(days) else current.addAll(days)
                salaryRepository.saveVacationDays(y, m - 1, current)
                vacationDaysMap = vacationDaysMap + (key to current)
            }
        }
        showVacationDialog = false
    }

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
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
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

        // Блок текущей смены «Сегодня» с обратным отсчётом (№9 из UI/UX)
        TodayShiftCard(
            brigade = viewingBrigade,
            primaryColor = primaryColor
        )

        // Выбор бригады для просмотра
        BrigadeSelector(
            selectedBrigade = viewingBrigade,
            onBrigadeSelected = { viewingBrigade = it },
            primaryColor = primaryColor
        )

        // Календарь
        MonthCalendarPager(
            visibleMonth = visibleMonth,
            selectedBrigade = viewingBrigade,
            onMonthChange = { visibleMonth = it },
            onGoToToday = { visibleMonth = YearMonth.from(today) },
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
            onDismiss = { showExportDialog = false }
        )
    }

    if (showVacationDialog) {
        AlertDialog(
            onDismissRequest = { showVacationDialog = false },
            title = { Text("Отпуск", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Выберите даты отпуска (от и до).", fontSize = 13.sp)
                    OutlinedButton(
                        onClick = {
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d -> vacationFrom = LocalDate.of(y, m + 1, d) },
                                vacationFrom.year, vacationFrom.monthValue - 1, vacationFrom.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("От: ${vacationFrom.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d -> vacationTo = LocalDate.of(y, m + 1, d) },
                                vacationTo.year, vacationTo.monthValue - 1, vacationTo.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("До: ${vacationTo.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { applyVacation(false) }) {
                    Text("Отметить", color = DesignTokens.Success, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { applyVacation(true) }) {
                        Text("Снять", color = DesignTokens.Danger, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showVacationDialog = false }) {
                        Text("Отмена", color = Color.Gray)
                    }
                }
            }
        )
    }
}
