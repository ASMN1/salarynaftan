package com.example.salarynaftan.ui
import com.example.salarynaftan.*
import com.example.salarynaftan.R

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.salarynaftan.util.weightFill
import org.koin.compose.koinInject
import java.util.Locale

@Composable
fun AlarmsTabScreen() {
    val context = LocalContext.current
    val settingsManager = koinInject<SettingsManager>()
    val scheduler = koinInject<AlarmScheduler>()
    var selectedBrigade by remember { mutableStateOf(settingsManager.getBrigade()) }

    // Оставляем карточку про автозапуск, только если приложение ещё
    // ограничено оптимизацией батареи — после исключения она скрывается.
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    var batteryOptimized by remember {
        mutableStateOf(!powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedBrigade = settingsManager.getBrigade()
                batteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ===== Заголовок =====
        PremiumHeader(
            title = stringResource(R.string.alarms_title),
            subtitle = stringResource(R.string.alarms_subtitle)
        )

        // ===== Подсказка про автозапуск (№29 из SECURITY/COMPATIBILITY) =====
        // Xiaomi/MIUI, Huawei/EMUI, Oppo/ColorOS, Vivo/Funtouch агрессивно
        // убивают фоновые процессы, из-за чего точные будильники могут не
        // сработать даже с разрешением exact alarm. Показываем совет только
        // на таких прошивках и пока оптимизация батареи не отключена.
        if (isAggressiveBatteryOem() && batteryOptimized) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFA726).copy(alpha = 0.14f)
                ),
                border = BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "⚠️ Если будильник не срабатывает",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFA726)
                    )
                    Text(
                        text = "На этой прошивке (${Build.MANUFACTURER}) система может блокировать фоновые будильники. " +
                                "Разрешите приложению «Автозапуск» в системных настройках, чтобы сигналы работали надёжно.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }) {
                        Text("Настроить автозапуск", color = Color(0xFFFFA726), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ===== Кнопка тестового будильника (№19 из UI/UX) =====
        AlarmSectionCard(
            title = "Проверка",
            subtitle = "Убедиться, что звук и вибрация работают",
            icon = "🧪"
        ) {
            var testStatus by remember { mutableStateOf<String?>(null) }
            var needsPermission by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val ok = scheduler.scheduleTestAlarm(10)
                        if (ok) {
                            testStatus = "Сигнал через 10 сек..."
                            needsPermission = false
                        } else {
                            needsPermission = true
                            testStatus = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Проверить будильник", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (testStatus != null) {
                    Text(testStatus!!, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = primary)
                }
            }
            if (needsPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Точные будильники отключены. Разрешите их, чтобы звук работал.",
                        fontSize = 12.sp,
                        color = Color(0xFFFF5252),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        )
                    }) {
                        Text("Разрешить", color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ===== Карточка сменных будильников =====
        AlarmSectionCard(
            title = "Сменные",
            subtitle = "Автоматически по графику · бригада $selectedBrigade",
            icon = "🔄"
        ) {
            // В Графике №2 нет дневной смены (только Утро/Ночь) — не показываем
            // переключатель «День», который молча не работал бы (п.2.3).
            val shiftTypes = if (settingsManager.getScheduleType() == ScheduleType.GRAPH_2) {
                listOf(ShiftType.MORNING, ShiftType.NIGHT)
            } else {
                listOf(ShiftType.MORNING, ShiftType.DAY, ShiftType.NIGHT)
            }
            shiftTypes.forEachIndexed { index, shiftType ->
                ShiftAlarmRow(
                    shiftType = shiftType,
                    brigade = selectedBrigade,
                    scheduler = scheduler,
                    onResult = { AppNotifier.show(it) }
                )
                if (index < 2) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )
                }
            }
        }

        var alarms by remember { mutableStateOf(scheduler.getRegularAlarms()) }

        // ===== Карточка обычных будильников =====
        AlarmSectionCard(
            title = "Обычные",
            subtitle = "Повторяющиеся ежедневные сигналы",
            icon = "🔔",
            trailing = {
                Surface(
                    onClick = {
                        val currentAlarms = scheduler.getRegularAlarms()
                        val newAlarm = RegularAlarm(
                            id = RegularAlarm.newId(currentAlarms),
                            time = "08:00",
                            isEnabled = true,
                            label = "Будильник"
                        )
                        val updated = currentAlarms + newAlarm
                        scheduler.saveRegularAlarms(updated)
                        alarms = updated
                        AppNotifier.show("Добавлен будильник")
                    },
                    shape = CircleShape,
                    color = primary,
                    contentColor = Color.Black
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить",
                        tint = Color.Black,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }
            }
        ) {
            if (alarms.isEmpty()) {
                EmptyAlarmPlaceholder(text = "Нет обычных будильников")
            } else {
                alarms.forEachIndexed { index, alarm ->
                    RegularAlarmRow(
                        alarm = alarm,
                        scheduler = scheduler,
                        onResult = { AppNotifier.show(it) },
                        onChanged = { newAlarms ->
                            alarms = newAlarms
                        }
                    )
                    if (index < alarms.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }

        // ===== Карточка авто-тишины =====
        AutoSilenceCard(scheduler = scheduler)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ===== Карточка-секция =====
@Composable
private fun AlarmSectionCard(
    title: String,
    subtitle: String? = null,
    icon: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

// ===== Строка сменного будильника =====
@Composable
private fun ShiftAlarmRow(
    shiftType: ShiftType,
    brigade: Int,
    scheduler: AlarmScheduler,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(scheduler.isAlarmScheduledForShift(shiftType, brigade)) }
    var times by remember { mutableStateOf(scheduler.getAlarmTimesForShift(shiftType, brigade)) }
    var showDialog by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(shiftType, brigade) {
        isEnabled = scheduler.isAlarmScheduledForShift(shiftType, brigade)
        times = scheduler.getAlarmTimesForShift(shiftType, brigade)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Цветной индикатор смены
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 32.dp)
                .background(
                    if (isEnabled) shiftType.color else shiftType.color.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
        )
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shiftType.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = shiftType.color.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = shiftType.shortName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color.Black else Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = times.joinToString("   •   "),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        // Кнопка редактирования времени
        Surface(
            onClick = { if (isEnabled) showDialog = true },
            enabled = isEnabled,
            shape = CircleShape,
            color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            contentColor = if (isEnabled) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Изменить",
                modifier = Modifier.padding(9.dp).size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                if (checked) {
                    val count = scheduler.scheduleAlarmsForShift(shiftType, brigade)
                    isEnabled = true
                    times = scheduler.getAlarmTimesForShift(shiftType, brigade)
                    onResult("$count будильника(ов)")
                } else {
                    scheduler.cancelAlarmsForShift(shiftType, brigade)
                    isEnabled = false
                    onResult("Отменены")
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = shiftType.color,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }

    if (showDialog) {
        var tempTimes by remember { mutableStateOf(times.toMutableList()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Время сигналов", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tempTimes.forEachIndexed { index, time ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val parts = time.split(":")
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            val newTime = String.format(Locale.US, "%02d:%02d", h, m)
                                            tempTimes = tempTimes.toMutableList().apply { this[index] = newTime }
                                        },
                                        parts[0].toIntOrNull() ?: 6,
                                        parts[1].toIntOrNull() ?: 0,
                                        true
                                    ).show()
                                },
                                modifier = weightFill(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Сигнал ${index + 1}:  $time", fontSize = 13.sp)
                            }
                            if (tempTimes.size > 1) {
                                IconButton(
                                    onClick = {
                                        tempTimes = tempTimes.toMutableList().apply { removeAt(index) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                    if (tempTimes.size < 10) {
                        OutlinedButton(
                            onClick = {
                                tempTimes = (tempTimes + "06:00").toMutableList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("+ Добавить сигнал", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        times = tempTimes
                        scheduler.saveAlarmTimesForShift(shiftType, tempTimes, brigade)
                        if (isEnabled) {
                            scheduler.cancelAlarmsForShift(shiftType, brigade)
                            scheduler.scheduleAlarmsForShift(shiftType, brigade)
                            onResult("Время обновлено")
                        }
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// ===== Строка обычного будильника =====
@Composable
private fun RegularAlarmRow(
    alarm: RegularAlarm,
    scheduler: AlarmScheduler,
    onResult: (String) -> Unit,
    onChanged: (List<RegularAlarm>) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(alarm.isEnabled) }
    var time by remember { mutableStateOf(alarm.time) }
    var label by remember { mutableStateOf(alarm.label) }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(alarm.id) {
        val refreshed = scheduler.getRegularAlarms().find { it.id == alarm.id }
        if (refreshed != null) {
            isEnabled = refreshed.isEnabled
            time = refreshed.time
            label = refreshed.label
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Цветная точка активности
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (isEnabled) primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = time,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        // Кнопка редактирования времени
        Surface(
            onClick = {
                val parts = time.split(":")
                android.app.TimePickerDialog(
                    context,
                    { _, h, m ->
                        val newTime = String.format(Locale.US, "%02d:%02d", h, m)
                        time = newTime
                        val updated = scheduler.getRegularAlarms().map {
                            if (it.id == alarm.id) it.copy(time = newTime, isEnabled = isEnabled) else it
                        }
                        scheduler.saveRegularAlarms(updated)
                        onChanged(updated)
                        if (isEnabled) {
                            scheduler.cancelSingleRegularAlarm(alarm.id)
                            updated.find { it.id == alarm.id }?.let {
                                scheduler.scheduleSingleRegularAlarm(it)
                            }
                        }
                        onResult("Время: $newTime")
                    },
                    parts[0].toIntOrNull() ?: 7,
                    parts[1].toIntOrNull() ?: 0,
                    true
                ).show()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            contentColor = primary
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Изменить",
                modifier = Modifier.padding(9.dp).size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                val updated = scheduler.getRegularAlarms().map {
                    if (it.id == alarm.id) it.copy(isEnabled = checked) else it
                }
                scheduler.saveRegularAlarms(updated)
                onChanged(updated)
                if (checked) {
                    updated.find { it.id == alarm.id }?.let {
                        scheduler.scheduleSingleRegularAlarm(it)
                    }
                    onResult("Включён")
                } else {
                    scheduler.cancelSingleRegularAlarm(alarm.id)
                    onResult("Выключен")
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Spacer(modifier = Modifier.width(4.dp))

        // Кнопка удаления
        Surface(
            onClick = {
                scheduler.cancelSingleRegularAlarm(alarm.id)
                val updated = scheduler.getRegularAlarms().filter { it.id != alarm.id }
                scheduler.saveRegularAlarms(updated)
                onChanged(updated)
                onResult("Удалён")
            },
            shape = CircleShape,
            color = Color(0xFFFF5252).copy(alpha = 0.1f),
            contentColor = Color(0xFFFF5252)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                modifier = Modifier.padding(9.dp).size(18.dp)
            )
        }
    }
}

// ===== Карточка авто-тишины =====
@Composable
private fun AutoSilenceCard(scheduler: AlarmScheduler) {
    val context = LocalContext.current
    // Настройки авто-тишины — в DataStore (п.6.8); prefs не используется.
    val settings = remember(context) { SettingsManager(context) }
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    var isEnabled by remember { mutableStateOf(settings.getAutoSilenceEnabled()) }
    var startTime by remember { mutableStateOf(settings.getAutoSilenceStart()) }
    var endTime by remember { mutableStateOf(settings.getAutoSilenceEnd()) }
    val primary = MaterialTheme.colorScheme.primary

    fun save(enabled: Boolean, start: String, end: String) {
        isEnabled = enabled
        startTime = start
        endTime = end
        settings.saveAutoSilenceEnabled(enabled)
        settings.saveAutoSilenceStart(start)
        settings.saveAutoSilenceEnd(end)
        scheduler.updateAutoSilenceAlarms(enabled, start, end)
    }

    AlarmSectionCard(
        title = "Авто-тишина",
        subtitle = "Автоматически после ночной смены",
        icon = "🌙"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) "Тихий режим включён" else "Тихий режим выключен",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (isEnabled) {
                    Text(
                        text = "С $startTime до $endTime",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    if (checked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                        && !notificationManager.isNotificationPolicyAccessGranted
                    ) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } else {
                        save(checked, startTime, endTime)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        AnimatedVisibility(visible = isEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeChip(
                    text = startTime,
                    onClick = {
                        val parts = startTime.split(":")
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> save(isEnabled, String.format(Locale.US, "%02d:%02d", h, m), endTime) },
                            parts[0].toIntOrNull() ?: 8,
                            parts[1].toIntOrNull() ?: 0,
                            true
                        ).show()
                    }
                )
                Text(
                    text = " — ",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                TimeChip(
                    text = endTime,
                    onClick = {
                        val parts = endTime.split(":")
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> save(isEnabled, startTime, String.format(Locale.US, "%02d:%02d", h, m)) },
                            parts[0].toIntOrNull() ?: 16,
                            parts[1].toIntOrNull() ?: 0,
                            true
                        ).show()
                    }
                )
            }
        }
    }
}

// ===== Чип времени =====
@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
    }
}

// ===== Плейсхолдер пустого списка =====
@Composable
private fun EmptyAlarmPlaceholder(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}

/**
 * Производители с агрессивным управлением фоновыми процессами (№29).
 * На их прошивках точные будильники могут не срабатывать, даже если
 * разрешение exact alarm выдано, — нужен «автозапуск» в системных настройках.
 */
private fun isAggressiveBatteryOem(): Boolean {
    val m = Build.MANUFACTURER.lowercase()
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("huawei") ||
            m.contains("honor") || m.contains("oppo") || m.contains("vivo") ||
            m.contains("oneplus") || m.contains("realme")
}
