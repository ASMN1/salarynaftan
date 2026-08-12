package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import com.example.salarynaftan.ui.SettingsViewModel.SettingsUiState
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ==========================================
// СЕКЦИИ ЭКРАНА НАСТРОЕК (п.3.2)
// Каждая настройка — отдельный переиспользуемый @Composable,
// чтобы SettingsScreen оставался тонким оркестратором.
// ==========================================

// ---- 1. ТЕМА ----
@Composable
fun ThemeSettingCard(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    primary: Color
) {
    PremiumSettingCard(
        icon = if (isDarkTheme) "🌙" else "☀️",
        title = "Тёмная тема",
        description = if (isDarkTheme) "Включена" else "Выключена"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDarkTheme) "Включена" else "Выключена",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            PremiumSwitch(
                checked = isDarkTheme,
                onCheckedChange = onThemeChange,
                trackColor = primary
            )
        }
    }
}

// ---- 2. ВСЕ НАСТРОЙКИ ЦВЕТОВ ----
@Composable
fun AppearanceSettingCard(
    viewState: SettingsUiState,
    primary: Color,
    onPrimaryPicker: () -> Unit,
    onBackgroundPicker: () -> Unit,
    onSurfacePicker: () -> Unit,
    onShiftColorPick: (ShiftType) -> Unit,
    onResetColors: () -> Unit
) {
    PremiumSettingCard(
        icon = "🎨",
        title = "Оформление",
        description = "Цвета приложения"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumDivider()
            PremiumColorRow(
                label = "Основной",
                color = viewState.primaryColor,
                onClick = onPrimaryPicker
            )
            PremiumColorRow(
                label = "Фон",
                color = viewState.backgroundColor,
                onClick = onBackgroundPicker
            )
            PremiumColorRow(
                label = "Карточки",
                color = viewState.surfaceColor,
                onClick = onSurfacePicker
            )
            PremiumDivider()
            Text(
                "Смены",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 2.dp)
            )
            listOf(
                "🌅 Утро" to viewState.morningColor to ShiftType.MORNING,
                "☀️ День" to viewState.dayColor to ShiftType.DAY,
                "🌙 Ночь" to viewState.nightColor to ShiftType.NIGHT,
                "📅 Выходной" to viewState.offColor to ShiftType.OFF
            ).forEach { (pair, type) ->
                val (label, color) = pair
                PremiumColorRow(
                    label = label,
                    color = color,
                    onClick = { onShiftColorPick(type) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onResetColors,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252).copy(alpha = 0.12f),
                    contentColor = Color(0xFFFF5252)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Сбросить все цвета",
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// ---- 2.4 МАСШТАБ ИНТЕРФЕЙСА ----
@Composable
fun UiScaleSettingCard(
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    primary: Color
) {
    PremiumSettingCard(
        icon = "🔍",
        title = "Масштаб интерфейса",
        description = "${(uiScale * 100).roundToInt()}%"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Мелкий", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                "${(uiScale * 100).roundToInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            Text("Крупный", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Slider(
            value = uiScale,
            onValueChange = onUiScaleChange,
            valueRange = 0.7f..1.5f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = primary,
                activeTrackColor = primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(22.dp)
        )
        Text(
            "Регулирует размер всех элементов и текста во вкладках",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

// ---- 2.5 ДИНАМИЧЕСКИЕ ЦВЕТА (MATERIAL YOU) ----
@Composable
fun DynamicColorsSettingCard(
    settings: SettingsManager,
    isDarkTheme: Boolean,
    primary: Color,
    onThemeChange: (Boolean) -> Unit,
    onResetColors: () -> Unit
) {
    // Material You доступен только на Android 12+ (API 31+). На старых
    // устройствах переключатель не показывается — иначе пользователь включит
    // его, но динамические цвета молча не применятся (п.4.3 аудита).
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return

    val useDynamicColors = remember { mutableStateOf(settings.getUseDynamicColors()) }
    PremiumSettingCard(
        icon = "✨",
        title = "Динамические цвета",
        description = if (useDynamicColors.value) "Material You" else "Вручную"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (useDynamicColors.value) "Material You" else "Вручную",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            PremiumSwitch(
                checked = useDynamicColors.value,
                onCheckedChange = {
                    useDynamicColors.value = it
                    settings.saveUseDynamicColors(it)
                    if (it) {
                        // Сбрасываем кастомные цвета, чтобы применить динамические
                        onResetColors()
                    }
                    onThemeChange(isDarkTheme) // recreate
                },
                trackColor = primary
            )
        }
    }
}

// ---- 2.6 OLED-РЕЖИМ ----
@Composable
fun OledSettingCard(
    useOled: Boolean,
    onOledChange: (Boolean) -> Unit,
    primary: Color
) {
    PremiumSettingCard(
        icon = "🖤",
        title = "OLED-режим",
        description = if (useOled) "Чисто чёрный фон" else "Стандартный"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (useOled) "Экономия батареи на AMOLED" else "Выкл",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            PremiumSwitch(
                checked = useOled,
                onCheckedChange = onOledChange,
                trackColor = primary
            )
        }
    }
}

// ---- 3. ГРОМКОСТЬ ----
@Composable
fun VolumeSettingCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    primary: Color
) {
    PremiumSettingCard(
        icon = "🔊",
        title = "Громкость",
        description = "${(volume * 100).toInt()}%"
    ) {
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            colors = SliderDefaults.colors(
                thumbColor = primary,
                activeTrackColor = primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(22.dp)
        )
    }
}

// ---- 3.5 НАРАСТАНИЕ ГРОМКОСТИ ----
@Composable
fun VolumeRampSettingCard(
    settings: SettingsManager,
    primary: Color
) {
    val rampSec = remember { mutableStateOf(settings.getVolumeRampSec()) }
    PremiumSettingCard(
        icon = "⏱️",
        title = "Нарастание громкости",
        description = "${rampSec.value} сек"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Быстро", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                "${rampSec.value} сек",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            Text("Плавно", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Slider(
            value = rampSec.value.toFloat(),
            onValueChange = {
                val s = it.toInt()
                rampSec.value = s
                settings.saveVolumeRampSec(s)
            },
            valueRange = 2f..30f,
            steps = 13,
            colors = SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(22.dp)
        )
        Text(
            "Длительность плавного нарастания громкости будильника до максимума",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

// ---- 4. МЕЛОДИЯ ----
@Composable
fun RingtoneSettingCard(
    viewState: SettingsUiState,
    ringtoneLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onPlayStop: () -> Unit,
    primary: Color
) {
    PremiumSettingCard(
        icon = "🎵",
        title = "Мелодия",
        description = viewState.ringtoneName
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 18.dp)) {
            Button(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        viewState.ringtoneUri?.let {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                        }
                    }
                    ringtoneLauncher.launch(intent)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Выбрать", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
            OutlinedButton(
                onClick = onPlayStop,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (viewState.isPlaying) "⏹  Стоп" else "▶  Слушать", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---- 5. ГРАФИК СМЕН + БРИГАДА (ОБЪЕДИНЁННЫЙ БЛОК) ----
@Composable
fun BrigadeAndScheduleCard(
    viewState: SettingsUiState,
    onScheduleTypeChange: (ScheduleType) -> Unit,
    onBrigadeChange: (Int) -> Unit,
    primary: Color
) {
    PremiumSettingCard(
        icon = "🗓️",
        title = "График и бригада",
        description = "${viewState.scheduleType.displayName} · Бригада ${viewState.brigade}"
    ) {
        // Выбор графика
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp)
        ) {
            ScheduleType.entries.forEach { type ->
                val selected = viewState.scheduleType == type
                Surface(
                    onClick = { onScheduleTypeChange(type) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "№${type.ordinal + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        // Выбор бригады
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            viewState.scheduleType.brigadeRange().forEach { num ->
                val selected = viewState.brigade == num
                Surface(
                    onClick = { onBrigadeChange(num) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
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
        Text(
            text = "${viewState.scheduleType.brigadeCount} бригад · смены по ${viewState.scheduleType.shiftHours.toInt()} ч",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 18.dp, top = 2.dp)
        )
    }
}

// ---- 5.4 ВИДЖЕТ ----
@Composable
fun WidgetSettingCard(primary: Color) {
    var showDialog by remember { mutableStateOf(false) }
    PremiumSettingCard(
        icon = "📱",
        title = "Виджет графика",
        description = "Добавьте на главный экран"
    ) {
        Button(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Как добавить виджет", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
        Text(
            "Показывает весь месяц смен, зарплату, аванс и праздники. Добавляется на главный экран телефона.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp)
        )
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("📱 Как добавить виджет", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1️⃣ Нажмите и удерживайте пустое место на главном экране телефона", fontSize = 14.sp)
                    Text("2️⃣ Выберите «Виджеты» в появившемся меню", fontSize = 14.sp)
                    Text("3️⃣ Найдите «Нафтан — Смена» в списке виджетов", fontSize = 14.sp)
                    Text("4️⃣ Перетащите виджет на главный экран", fontSize = 14.sp)
                    Text("5️⃣ При добавлении выберите свою бригаду", fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Понятно", fontWeight = FontWeight.Bold, color = primary)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// ---- 5.5 ПРЕД-НАПОМИНАНИЕ О СМЕНЕ ----
@Composable
fun ShiftReminderSettingCard(
    settings: SettingsManager,
    scheduler: AlarmScheduler,
    primary: Color
) {
    val reminderEnabled = remember { mutableStateOf(settings.getShiftReminderMinutes() > 0) }
    val reminderMinutes = remember { mutableStateOf(settings.getShiftReminderMinutes()) }
    PremiumSettingCard(
        icon = "⏰",
        title = "Напоминать о смене",
        description = if (reminderEnabled.value) "За ${reminderMinutes.value} мин" else "Выключено"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (reminderEnabled.value) "Напоминание за ${reminderMinutes.value} мин" else "Выключено",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            PremiumSwitch(
                checked = reminderEnabled.value,
                onCheckedChange = { checked ->
                    reminderEnabled.value = checked
                    reminderMinutes.value = if (checked) reminderMinutes.value.coerceAtLeast(5) else 0
                    settings.saveShiftReminderMinutes(reminderMinutes.value)
                    // Применяем новое значение к уже запланированным сменным
                    // будильникам текущей бригады, иначе изменение вступит в
                    // силу только при следующем перепланировании (п.6.7).
                    scheduler.rescheduleAllAlarmsForBrigade(settings.getBrigade())
                },
                trackColor = primary
            )
        }
        if (reminderEnabled.value) {
            Slider(
                value = reminderMinutes.value.toFloat(),
                onValueChange = {
                    reminderMinutes.value = it.toInt().coerceIn(5, 180)
                    settings.saveShiftReminderMinutes(reminderMinutes.value)
                },
                onValueChangeFinished = {
                    scheduler.rescheduleAllAlarmsForBrigade(settings.getBrigade())
                },
                valueRange = 5f..180f,
                steps = 34,
                colors = SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(22.dp)
            )
            Text(
                "Показывать уведомление за выбранное время до начала смены",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

// ---- 6. О ПРИЛОЖЕНИИ ----
@Composable
fun AboutSettingCard(primary: Color) {
    val context = LocalContext.current
    PremiumSettingCard(
        icon = "ℹ️",
        title = "О приложении",
        description = "Версия ${getAppVersion(context)}"
    ) {
        var showAbout by remember { mutableStateOf(false) }
        Button(
            onClick = { showAbout = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Открыть", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                modifier = Modifier.fillMaxSize(),
                title = null,
                text = {
                    Box(Modifier.fillMaxSize()) {
                        AboutScreen()
                        IconButton(
                            onClick = { showAbout = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text("✕", fontSize = 20.sp, color = Color.White)
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

// ===== КАРТОЧКА НАСТРОЙКИ =====
@Composable
fun PremiumSettingCard(
    icon: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    PremiumSectionCard {
        Column {
            PremiumSectionTitle(icon = icon, title = title, subtitle = description)
            PremiumDivider()
            Spacer(modifier = Modifier.height(8.dp))
            content()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ===== СТРОКА ЦВЕТА =====
@Composable
fun PremiumColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
        )
    }
}