package com.example.salarynaftan

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.compose.koinInject
import java.util.Locale
import android.content.Context

@Composable
fun AlarmsTabScreen() {
    val context = LocalContext.current
    val settingsManager = koinInject<SettingsManager>()
    val scheduler = koinInject<AlarmScheduler>()
    var selectedBrigade by remember { mutableStateOf(settingsManager.getBrigade()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedBrigade = settingsManager.getBrigade()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var popupMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⏰ Будильники",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, AlarmRingingActivity::class.java).apply {
                        putExtra("alarm_title", "Тест будильника")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("🔔 Тест", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        // ===== 1. Сменные будильники =====
        CompactCard(
            title = "Сменные (бр $selectedBrigade)"
        ) {
            listOf(ShiftType.MORNING, ShiftType.DAY, ShiftType.NIGHT).forEachIndexed { index, shiftType ->
                CompactShiftAlarmItem(
                    shiftType = shiftType,
                    brigade = selectedBrigade,
                    scheduler = scheduler,
                    onResult = { popupMessage = it }
                )
                if (index < 2) {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f), thickness = 0.5.dp)
                }
            }
        }

        // ===== 2. Обычные будильники =====
        CompactCard(
            title = "Обычные",
            actions = {
                IconButton(
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
                        popupMessage = "Добавлен будильник"
                    },
                    modifier = Modifier.size(22.dp)
                ) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        ) {
            val alarms = scheduler.getRegularAlarms()
            if (alarms.isEmpty()) {
                Text("Нет будильников", color = Color.Gray, fontSize = 12.sp)
            } else {
                alarms.forEachIndexed { index, alarm ->
                    CompactRegularAlarmItem(
                        alarm = alarm,
                        scheduler = scheduler,
                        onResult = { popupMessage = it }
                    )
                    if (index < alarms.size - 1) {
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // ===== 3. Авто-тишина =====
        CompactAutoSilenceCard(scheduler = scheduler)

        popupMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { popupMessage = null },
                confirmButton = {
                    TextButton(onClick = { popupMessage = null }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                },
                title = { Text("Информация", fontSize = 12.sp) },
                text = { Text(message, fontSize = 11.sp) }
            )
        }
    }
}

// ===== КОМПАКТНАЯ КАРТОЧКА =====
@Composable
private fun CompactCard(
    title: String,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.DarkGray),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                actions?.invoke()
            }
            content()
        }
    }
}

// ===== КОМПАКТНЫЙ СМЕННЫЙ БУДИЛЬНИК =====
@Composable
private fun CompactShiftAlarmItem(
    shiftType: ShiftType,
    brigade: Int,
    scheduler: AlarmScheduler,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(scheduler.isAlarmScheduledForShift(shiftType, brigade)) }
    var times by remember { mutableStateOf(scheduler.getAlarmTimesForShift(shiftType, brigade)) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(shiftType, brigade) {
        isEnabled = scheduler.isAlarmScheduledForShift(shiftType, brigade)
        times = scheduler.getAlarmTimesForShift(shiftType, brigade)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${shiftType.displayName} (${shiftType.shortName})",
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
            Text(
                text = times.joinToString("  •  "),
                fontSize = 9.sp,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }

        IconButton(
            onClick = { showDialog = true },
            enabled = isEnabled,
            modifier = Modifier.size(24.dp)
        ) {
            Text("🕒", fontSize = 12.sp)
        }

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
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ),
            modifier = Modifier.height(24.dp)
        )
    }

    if (showDialog) {
        var tempTimes by remember { mutableStateOf(times.toMutableList()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Время сигналов", fontSize = 12.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Сигнал ${index+1}: $time", fontSize = 12.sp)
                            }
                            if (tempTimes.size > 1) {
                                IconButton(
                                    onClick = {
                                        tempTimes = tempTimes.toMutableList().apply { removeAt(index) }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("✕", fontSize = 14.sp, color = Color.Red)
                                }
                            }
                        }
                    }
                    if (tempTimes.size < 10) {
                        Button(
                            onClick = {
                                tempTimes = (tempTimes + "06:00").toMutableList()
                            },
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+ Добавить", color = Color.White, fontSize = 11.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }
}

// ===== КОМПАКТНЫЙ ОБЫЧНЫЙ БУДИЛЬНИК =====
@Composable
private fun CompactRegularAlarmItem(
    alarm: RegularAlarm,
    scheduler: AlarmScheduler,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(alarm.isEnabled) }
    var time by remember { mutableStateOf(alarm.time) }
    var label by remember { mutableStateOf(alarm.label) }

    LaunchedEffect(alarm.id) {
        val refreshed = scheduler.getRegularAlarms().find { it.id == alarm.id }
        if (refreshed != null) {
            isEnabled = refreshed.isEnabled
            time = refreshed.time
            label = refreshed.label
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
            Text(
                text = time,
                fontSize = 9.sp,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }

        IconButton(
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
            modifier = Modifier.size(24.dp)
        ) {
            Text("🕒", fontSize = 12.sp)
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                val updated = scheduler.getRegularAlarms().map {
                    if (it.id == alarm.id) it.copy(isEnabled = checked) else it
                }
                scheduler.saveRegularAlarms(updated)
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
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ),
            modifier = Modifier.height(24.dp)
        )

        IconButton(
            onClick = {
                scheduler.cancelSingleRegularAlarm(alarm.id)
                val updated = scheduler.getRegularAlarms().filter { it.id != alarm.id }
                scheduler.saveRegularAlarms(updated)
                onResult("Удалён")
            },
            modifier = Modifier.size(22.dp)
        ) {
            Text("✕", fontSize = 12.sp, color = Color.Red)
        }
    }
}

// ===== КОМПАКТНАЯ АВТО-ТИШИНА =====
@Composable
private fun CompactAutoSilenceCard(scheduler: AlarmScheduler) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceKeys.AUTO_SILENCE_PREFS, Context.MODE_PRIVATE)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    var isEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.AUTO_SILENCE_ENABLED, false)) }
    var startTime by remember { mutableStateOf(prefs.getString(PreferenceKeys.AUTO_SILENCE_START, "08:00") ?: "08:00") }
    var endTime by remember { mutableStateOf(prefs.getString(PreferenceKeys.AUTO_SILENCE_END, "16:00") ?: "16:00") }

    fun save(enabled: Boolean, start: String, end: String) {
        isEnabled = enabled
        startTime = start
        endTime = end
        prefs.edit()
            .putBoolean(PreferenceKeys.AUTO_SILENCE_ENABLED, enabled)
            .putString(PreferenceKeys.AUTO_SILENCE_START, start)
            .putString(PreferenceKeys.AUTO_SILENCE_END, end)
            .apply()
        scheduler.updateAutoSilenceAlarms(enabled, start, end)
    }

    CompactCard(
        title = "Авто-тишина"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "После ночной смены",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
                if (isEnabled) {
                    Text(
                        text = "$startTime – $endTime",
                        fontSize = 9.sp,
                        color = Color.Gray
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
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(24.dp)
            )
        }
        if (isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val parts = startTime.split(":")
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m ->
                                save(isEnabled, String.format(Locale.US, "%02d:%02d", h, m), endTime)
                            },
                            parts[0].toIntOrNull() ?: 8,
                            parts[1].toIntOrNull() ?: 0,
                            true
                        ).show()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    Text(startTime, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text("—", fontSize = 10.sp, color = Color.Gray)
                OutlinedButton(
                    onClick = {
                        val parts = endTime.split(":")
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m ->
                                save(isEnabled, startTime, String.format(Locale.US, "%02d:%02d", h, m))
                            },
                            parts[0].toIntOrNull() ?: 16,
                            parts[1].toIntOrNull() ?: 0,
                            true
                        ).show()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    Text(endTime, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}