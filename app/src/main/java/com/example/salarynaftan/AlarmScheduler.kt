package com.example.salarynaftan

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// ==========================================
// 1. МОДЕЛЬ СМЕН И РАСПИСАНИЕ НА 5 БРИГАД
// ==========================================
enum class ShiftType(
    val displayName: String,
    val shortName: String,
    val color: Color,
    val startTime: LocalTime?,
    val endTime: LocalTime?
) {
    OFF("Выходной", "В", Color(0xFFF8EDF3), null, null),
    DAY("День", "Д", Color(0xFFA2D39C), LocalTime.of(16, 0), LocalTime.of(0, 0)),
    MORNING("Утро", "У", Color(0xFFFEE45B), LocalTime.of(8, 0), LocalTime.of(16, 0)),
    NIGHT("Ночь", "Н", Color(0xFF4F6D91), LocalTime.of(0, 0), LocalTime.of(8, 0))
}

object ShiftSchedule {
    private val ANCHOR_DATE: LocalDate = LocalDate.of(2026, 1, 1)

    private val CYCLE = listOf(
        ShiftType.OFF,     // 0
        ShiftType.OFF,     // 1
        ShiftType.DAY,     // 2
        ShiftType.DAY,     // 3
        ShiftType.OFF,     // 4
        ShiftType.MORNING, // 5
        ShiftType.MORNING, // 6
        ShiftType.NIGHT,   // 7
        ShiftType.NIGHT,   // 8
        ShiftType.OFF      // 9
    )

    private fun getOffsetForBrigade(brigade: Int): Int {
        return when (brigade) {
            1 -> 0
            2 -> 4
            3 -> 6
            4 -> 2
            5 -> 8
            else -> 0
        }
    }

    fun shiftFor(date: LocalDate, brigade: Int = 1): ShiftType {
        val diff = ChronoUnit.DAYS.between(ANCHOR_DATE, date)
        val offset = getOffsetForBrigade(brigade)
        var idx = ((diff + offset) % CYCLE.size) % CYCLE.size
        if (idx < 0) idx += CYCLE.size
        return CYCLE[idx.toInt()]
    }
}

// ==========================================
// 2. МОДЕЛЬ ОБЫЧНОГО БУДИЛЬНИКА
// ==========================================
data class RegularAlarm(
    val id: Long,
    val time: String,
    val isEnabled: Boolean,
    val label: String
)

// ==========================================
// 3. МЕНЕДЖЕР ПЛАНИРОВАНИЯ БУДИЛЬНИКОВ И ТИШИНЫ
// ==========================================
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("alarm_scheduler_prefs", Context.MODE_PRIVATE)
    private val settingsManager = SettingsManager(context)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isAlarmScheduledForShift(type: ShiftType, brigade: Int): Boolean {
        return prefs.getBoolean("shift_alarm_${brigade}_${type.name}", false)
    }

    fun getAlarmTimesForShift(type: ShiftType, brigade: Int): List<String> {
        val defaultList = when (type) {
            ShiftType.MORNING -> "06:00"
            ShiftType.DAY -> "14:00"
            ShiftType.NIGHT -> "22:00"
            ShiftType.OFF -> "08:00"
        }
        val saved = prefs.getString("shift_times_${brigade}_${type.name}", defaultList) ?: defaultList
        return saved.split(",").filter { it.isNotBlank() }
    }

    fun saveAlarmTimesForShift(type: ShiftType, times: List<String>, brigade: Int) {
        prefs.edit().putString("shift_times_${brigade}_${type.name}", times.joinToString(",")).apply()
    }

    fun scheduleAlarmsForShift(type: ShiftType, brigade: Int): Int {
        cancelAlarmsForShiftQuiet(type, brigade)

        prefs.edit()
            .putBoolean("shift_alarm_${brigade}_${type.name}", true)
            .apply()

        val times = getAlarmTimesForShift(type, brigade)

        times.forEachIndexed { index, timeStr ->
            scheduleSingleShiftAlarm(
                type = type,
                brigade = brigade,
                index = index,
                timeStr = timeStr
            )
        }

        return times.size
    }

fun scheduleSingleShiftAlarm(
        type: ShiftType,
        brigade: Int,
        index: Int,
        timeStr: String
    ) {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 6
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        var targetDate = LocalDate.now()
        val nowTime = LocalTime.now()
        val candidateToday = LocalTime.of(hour, minute)

        if (ShiftSchedule.shiftFor(targetDate, brigade) != type || !candidateToday.isAfter(nowTime)) {
            targetDate = targetDate.plusDays(1)

            while (ShiftSchedule.shiftFor(targetDate, brigade) != type) {
                targetDate = targetDate.plusDays(1)
            }
        }

        val targetMillis = LocalDateTime.of(
            targetDate,
            LocalTime.of(hour, minute)
        ).atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", "Бр $brigade: ${type.displayName} смена")
            putExtra("shift_type_name", type.name)
            putExtra("alarm_index", index)
            putExtra("brigade", brigade)
        }

        val requestCode = brigade * 1000 + type.ordinal * 100 + index

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)

        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    val info = AlarmManager.AlarmClockInfo(targetMillis, showPendingIntent)

    try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(info, pendingIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    }

    private fun cancelAlarmsForShiftQuiet(type: ShiftType, brigade: Int) {
        // Всегда очищаем весь возможный диапазон индексов (от 0 до 9) для этой бригады и типа смены
        for (index in 0 until 10) {
            val requestCode = brigade * 1000 + type.ordinal * 100 + index
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    fun cancelAlarmsForShift(type: ShiftType, brigade: Int) {
        prefs.edit().putBoolean("shift_alarm_${brigade}_${type.name}", false).apply()
        cancelAlarmsForShiftQuiet(type, brigade)
    }

    private fun cancelAllShiftAlarmsAcrossAllBrigades() {
        for (b in 1..5) {
            ShiftType.values().forEach { type ->
                cancelAlarmsForShiftQuiet(type, b)
            }
        }
    }

    fun rescheduleAllAlarmsForBrigade(brigade: Int) {
        cancelAllShiftAlarmsAcrossAllBrigades()
        ShiftType.values().forEach { type ->
            if (isAlarmScheduledForShift(type, brigade)) {
                scheduleAlarmsForShift(type, brigade)
            }
        }
    }

    fun getRegularAlarms(): List<RegularAlarm> {
        val raw = prefs.getString("regular_alarms_list", null) ?: return listOf(
            RegularAlarm(1L, "07:30", false, "Утренний"),
            RegularAlarm(2L, "21:00", false, "Вечерний")
        )
        return raw.split(";").mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 4) {
                RegularAlarm(
                    id = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
                    time = parts[1],
                    isEnabled = parts[2].toBoolean(),
                    label = parts[3]
                )
            } else null
        }
    }

    fun saveRegularAlarms(alarms: List<RegularAlarm>) {
        val serialized = alarms.joinToString(";") { "${it.id}|${it.time}|${it.isEnabled}|${it.label}" }
        prefs.edit().putString("regular_alarms_list", serialized).apply()

        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                scheduleSingleRegularAlarm(alarm)
            } else {
                cancelSingleRegularAlarm(alarm.id)
            }
        }
    }

    fun scheduleSingleRegularAlarm(alarm: RegularAlarm) {
        val parts = alarm.time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = LocalDateTime.now()
        var targetTime = now.withHour(hour).withMinute(minute).withSecond(0)
        if (targetTime.isBefore(now)) {
            targetTime = targetTime.plusDays(1)
        }

        val triggerMillis = targetTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", alarm.label)
        }

        val requestCode = (10000 + alarm.id).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    triggerMillis,
                    showPendingIntent
                )

                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelSingleRegularAlarm(alarmId: Long) {
        val requestCode = (10000 + alarmId).toInt()
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun updateAutoSilenceAlarms(isEnabled: Boolean, startTime: String, endTime: String) {
        val intentOn = Intent(context, SilentModeReceiver::class.java).apply { action = "com.example.salarynaftan.ACTION_SILENT_ON" }
        val intentOff = Intent(context, SilentModeReceiver::class.java).apply { action = "com.example.salarynaftan.ACTION_SILENT_OFF" }

        // Безопасные requestCode (изменили с 2001/2002 на 90001/90002 во избежание коллизий с 2-й бригадой)
        val piOn = PendingIntent.getBroadcast(context, 90001, intentOn, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piOff = PendingIntent.getBroadcast(context, 90002, intentOff, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(piOn)
        alarmManager.cancel(piOff)

        if (isEnabled) {
            val now = LocalDateTime.now()
            val hOn = startTime.substringBefore(":").toIntOrNull() ?: 8
            val mOn = startTime.substringAfter(":").toIntOrNull() ?: 0
            var startLdt = now.withHour(hOn).withMinute(mOn).withSecond(0)
            if (startLdt.isBefore(now)) startLdt = startLdt.plusDays(1)

            val hOff = endTime.substringBefore(":").toIntOrNull() ?: 16
            val mOff = endTime.substringAfter(":").toIntOrNull() ?: 0
            var endLdt = now.withHour(hOff).withMinute(mOff).withSecond(0)
            if (endLdt.isBefore(now)) endLdt = endLdt.plusDays(1)

            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        startLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        piOn
                    )

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        piOff
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rescheduleAllAfterBoot() {
        val currentBrigade = settingsManager.getBrigade()
        ShiftType.values().forEach { type ->
            if (isAlarmScheduledForShift(type, currentBrigade)) {
                scheduleAlarmsForShift(type, currentBrigade)
            }
        }
        getRegularAlarms().forEach { alarm ->
            if (alarm.isEnabled) {
                scheduleSingleRegularAlarm(alarm)
            }
        }
        val autoPrefs = context.getSharedPreferences("auto_silence_prefs", Context.MODE_PRIVATE)
        if (autoPrefs.getBoolean("auto_silence_enabled", false)) {
            val start = autoPrefs.getString("auto_silence_start", "08:00") ?: "08:00"
            val end = autoPrefs.getString("auto_silence_end", "16:00") ?: "16:00"
            updateAutoSilenceAlarms(true, start, end)
        }
    }
}

// ==========================================
// 4. ЭКРАН ГРАФИКА СМЕН И ВЫБОР БРИГАДЫ
// ==========================================
@Composable
fun ScheduleScreen(isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var popupMessage by remember { mutableStateOf<String?>(null) }

    val scheduler = remember { AlarmScheduler(context) }
    val settingsManager = remember { SettingsManager(context) }
    var selectedBrigade by remember { mutableStateOf(settingsManager.getBrigade()) }

    var exactAlarmsAllowed by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAllowed = scheduler.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(popupMessage) {
        if (popupMessage != null) {
            kotlinx.coroutines.delay(2500)
            popupMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252)),
                border = BorderStroke(1.dp, Color(0xFFFF5252))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Требуется разрешение", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), fontSize = 13.sp)
                    Text("Для точной работы будильников разрешите доступ в настройках.", fontSize = 11.sp)
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Открыть настройки", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // Панель переключения бригад (1-5)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ВЫБОР БРИГАДЫ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..5).forEach { brigadeNum ->
                        val isSelected = selectedBrigade == brigadeNum
                        Button(
                            onClick = {
                                selectedBrigade = brigadeNum
                                settingsManager.setBrigade(brigadeNum)

                                // Переключаем будильники на выбранную бригаду
                                scheduler.rescheduleAllAlarmsForBrigade(brigadeNum)

                                popupMessage = "Включена Бригада $brigadeNum"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF00E676) else Color.DarkGray
                            ),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Бр $brigadeNum",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }

        MonthCalendar(visibleMonth = visibleMonth, selectedBrigade = selectedBrigade, onMonthChange = { visibleMonth = it })

        popupMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00E676))
            ) {
                Text(text = msg, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
        }

        Text(
            text = "БУДИЛЬНИКИ БРИГАДЫ $selectedBrigade",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E676),
            modifier = Modifier.padding(top = 4.dp)
        )

        listOf(ShiftType.MORNING, ShiftType.DAY, ShiftType.NIGHT).forEach { type ->
            ShiftAlarmRow(type = type, brigade = selectedBrigade, scheduler = scheduler, onResult = { popupMessage = it })
        }

        Spacer(modifier = Modifier.height(6.dp))

        RegularAlarmsSection(scheduler = scheduler, onResult = { popupMessage = it })

        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
        ShiftSilentModeCard()
    }
}

// ==========================================
// 5. ИНТЕРАКТИВНЫЙ КАЛЕНДАРЬ С HORIZONTAL PAGER
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendar(visibleMonth: YearMonth, selectedBrigade: Int, onMonthChange: (YearMonth) -> Unit) {
    val today = remember { LocalDate.now() }
    val baseMonth = remember { YearMonth.now() }

    val initialPage = 1200
    val currentPageTarget = remember(visibleMonth) {
        initialPage + (visibleMonth.year - baseMonth.year) * 12 + (visibleMonth.monthValue - baseMonth.monthValue)
    }

    val pagerState = rememberPagerState(
        initialPage = currentPageTarget,
        pageCount = { 2400 }
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

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth()
    ) { page ->
        val monthForPage = remember(page) {
            baseMonth.plusMonths((page - initialPage).toLong())
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val monthName = monthForPage.month.getDisplayName(TextStyle.FULL, Locale("ru")).replaceFirstChar { it.uppercase() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onMonthChange(monthForPage.minusMonths(1)) }) {
                        Text("<", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                    }
                    Text("$monthName ${monthForPage.year}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onMonthChange(monthForPage.plusMonths(1)) }) {
                        Text(">", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                    }
                }

                val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekDays.forEach { day ->
                        Text(text = day, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }

                val firstDayOfMonth = monthForPage.atDay(1)
                val daysInMonth = monthForPage.lengthOfMonth()
                val emptySlotsBefore = firstDayOfMonth.dayOfWeek.value - 1
                val totalCells = emptySlotsBefore + daysInMonth
                val rows = (totalCells + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - emptySlotsBefore + 1

                            if (dayNumber in 1..daysInMonth) {
                                val date = monthForPage.atDay(dayNumber)
                                val shift = ShiftSchedule.shiftFor(date, selectedBrigade)
                                val isToday = date == today

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .height(44.dp)
                                        .background(
                                            color = shift.color.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            width = if (isToday) 2.dp else 0.5.dp,
                                            color = if (isToday) Color(0xFF00E676) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(text = "$dayNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        Text(text = shift.shortName, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).padding(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. СТРОКА СМЕННОГО БУДИЛЬНИКА
// ==========================================
@Composable
fun ShiftAlarmRow(type: ShiftType, brigade: Int, scheduler: AlarmScheduler, onResult: (String) -> Unit) {
    val context = LocalContext.current
    var isEnabled by remember(type, brigade) { mutableStateOf(scheduler.isAlarmScheduledForShift(type, brigade)) }
    var alarmTimes by remember(type, brigade) { mutableStateOf(scheduler.getAlarmTimesForShift(type, brigade)) }
    var showDialog by remember { mutableStateOf(false) }

    val shiftTitle = "${type.displayName} (${type.shortName})"

    if (showDialog) {
        var tempTimes by remember { mutableStateOf(alarmTimes.toMutableList()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Будильники на смену (Бр $brigade)", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Укажите время сигналов:", fontSize = 12.sp, color = Color.Gray)

                    tempTimes.forEachIndexed { index, timeStr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val parts = timeStr.split(":")
                                    android.app.TimePickerDialog(context, { _, h, m ->
                                        tempTimes = tempTimes.toMutableList().apply {
                                            this[index] = String.format(Locale.US, "%02d:%02d", h, m)
                                        }
                                    }, parts.getOrNull(0)?.toIntOrNull() ?: 6, parts.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Сигнал ${index + 1}: $timeStr", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            if (tempTimes.size > 1) {
                                IconButton(onClick = { tempTimes = tempTimes.toMutableList().apply { removeAt(index) } }) {
                                    Text("❌", fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    if (tempTimes.size < 10) {
                        Button(
                            onClick = { tempTimes = (tempTimes + (tempTimes.lastOrNull() ?: "06:00")).toMutableList() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Добавить ещё будильник", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        alarmTimes = tempTimes
                        scheduler.saveAlarmTimesForShift(type, tempTimes, brigade)
                        showDialog = false
                        if (isEnabled) {
                            val count = scheduler.scheduleAlarmsForShift(type, brigade)
                            onResult("Сохранено $count будильника(ов) для Бр $brigade")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isEnabled) Color(0xFF00E676) else Color.DarkGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = shiftTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = alarmTimes.joinToString("  •  "), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00E676))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Настроить", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                }

                Button(
                    onClick = {
                        if (isEnabled) {
                            scheduler.cancelAlarmsForShift(type, brigade)
                            isEnabled = false
                            onResult("Будильники для Бр $brigade отменены")
                        } else {
                            val count = scheduler.scheduleAlarmsForShift(type, brigade)
                            isEnabled = true
                            onResult("Установлено $count будильника(ов) для Бр $brigade")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isEnabled) Color(0xFF00E676) else Color(0xFFFF5252)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isEnabled) "ВКЛ" else "ОТКЛ", color = if (isEnabled) Color.Black else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==========================================
// 7. СЕКЦИЯ ОБЫЧНЫХ БУДИЛЬНИКОВ
// ==========================================
@Composable
fun RegularAlarmsSection(scheduler: AlarmScheduler, onResult: (String) -> Unit) {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(scheduler.getRegularAlarms()) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ОБЫЧНЫЕ БУДИЛЬНИКИ",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676)
            )
            OutlinedButton(
                onClick = {
                    val newAlarm = RegularAlarm(System.currentTimeMillis(), "08:00", true, "Будильник")
                    val updated = alarms + newAlarm
                    alarms = updated
                    scheduler.saveRegularAlarms(updated)
                    onResult("Добавлен новый будильник")
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("+ Добавить", fontSize = 11.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
            }
        }

        alarms.forEach { alarm ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (alarm.isEnabled) Color(0xFF00E676) else Color.DarkGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = alarm.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = alarm.time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val parts = alarm.time.split(":")
                                android.app.TimePickerDialog(context, { _, h, m ->
                                    val newTime = String.format(Locale.US, "%02d:%02d", h, m)
                                    val updated = alarms.map { if (it.id == alarm.id) it.copy(time = newTime) else it }
                                    alarms = updated
                                    scheduler.saveRegularAlarms(updated)
                                    onResult("Время изменено на $newTime")
                                }, parts.getOrNull(0)?.toIntOrNull() ?: 7, parts.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Время", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                        }

                        IconButton(
                            onClick = {
                                scheduler.cancelSingleRegularAlarm(alarm.id)
                                val updated = alarms.filter { it.id != alarm.id }
                                alarms = updated
                                scheduler.saveRegularAlarms(updated)
                                onResult("Будильник удалён")
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("❌", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val newState = !alarm.isEnabled
                                val updated = alarms.map { if (it.id == alarm.id) it.copy(isEnabled = newState) else it }
                                alarms = updated
                                scheduler.saveRegularAlarms(updated)
                                onResult(if (newState) "Будильник включен" else "Будильник выключен")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (alarm.isEnabled) Color(0xFF00E676) else Color(0xFFFF5252)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = if (alarm.isEnabled) "ВКЛ" else "ОТКЛ", color = if (alarm.isEnabled) Color.Black else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. КАРТОЧКА АВТО-ТИШИНЫ С ЗАПРОСОМ DND
// ==========================================
@Composable
fun ShiftSilentModeCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auto_silence_prefs", Context.MODE_PRIVATE) }
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    var isEnabled by remember { mutableStateOf(prefs.getBoolean("auto_silence_enabled", false)) }
    var startTime by remember { mutableStateOf(prefs.getString("auto_silence_start", "08:00") ?: "08:00") }
    var endTime by remember { mutableStateOf(prefs.getString("auto_silence_end", "16:00") ?: "16:00") }

    fun saveSettings(enabled: Boolean, start: String, end: String) {
        isEnabled = enabled
        startTime = start
        endTime = end
        prefs.edit()
            .putBoolean("auto_silence_enabled", enabled)
            .putString("auto_silence_start", start)
            .putString("auto_silence_end", end)
            .apply()

        AlarmScheduler(context).updateAutoSilenceAlarms(enabled, start, end)
    }

    val dndLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
            saveSettings(true, startTime, endTime)
        } else {
            isEnabled = false
        }
    }

    fun showTimePicker(currentTime: String, onTimeSet: (String) -> Unit) {
        val parts = currentTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                onTimeSet(newTime)
            },
            h, m, true
        ).show()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Авто-тишина после ночной", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                    Text("Беззвучный режим в день отсыпного", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notificationManager.isNotificationPolicyAccessGranted) {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            dndLauncher.launch(intent)
                        } else {
                            saveSettings(checked, startTime, endTime)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676))
                )
            }

            if (isEnabled) {
                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Время работы:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                showTimePicker(startTime) { newStart ->
                                    saveSettings(isEnabled, newStart, endTime)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(startTime, fontSize = 12.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }

                        Text("—", fontSize = 12.sp, color = Color.Gray)

                        OutlinedButton(
                            onClick = {
                                showTimePicker(endTime) { newEnd ->
                                    saveSettings(isEnabled, startTime, newEnd)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(endTime, fontSize = 12.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}