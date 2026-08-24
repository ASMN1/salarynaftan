package com.example.salarynaftan.ui
import com.example.salarynaftan.*
import com.example.salarynaftan.R

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onColorsChange: (Color, Color, Color) -> Unit,
    currentPrimaryColor: Color,
    currentBackgroundColor: Color,
    currentSurfaceColor: Color,
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    useOled: Boolean = false,
    onOledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = koinViewModel<SettingsViewModel>()
    val settings = koinInject<SettingsManager>()
    val scheduler = koinInject<AlarmScheduler>()
    val viewState by viewModel.uiState.collectAsState()
    val primary = MaterialTheme.colorScheme.primary

    var showColorPicker by remember { mutableStateOf(false) }
    var selectedShiftType by remember { mutableStateOf<ShiftType?>(null) }
    var showPrimaryPicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showSurfacePicker by remember { mutableStateOf(false) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                viewModel.setRingtoneUri(uri)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPlayback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        PremiumHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle)
        )

        LegacyYearRecoverySection(primary = primary)

        // ---- 1. ТЕМА ----
        ThemeSettingCard(
            isDarkTheme = isDarkTheme,
            onThemeChange = { viewModel.setTheme(it, onThemeChange) },
            primary = primary
        )

        // ---- 2. ВСЕ НАСТРОЙКИ ЦВЕТОВ ----
        AppearanceSettingCard(
            viewState = viewState,
            primary = primary,
            onPrimaryPicker = { showPrimaryPicker = true },
            onBackgroundPicker = { showBackgroundPicker = true },
            onSurfacePicker = { showSurfacePicker = true },
            onShiftColorPick = { type ->
                selectedShiftType = type
                showColorPicker = true
            },
            onResetColors = { viewModel.resetAllColors(onColorsChange) }
        )

        // ---- 2.4 МАСШТАБ ИНТЕРФЕЙСА ----
        UiScaleSettingCard(
            uiScale = uiScale,
            onUiScaleChange = onUiScaleChange,
            primary = primary
        )

        // ---- 2.5 ДИНАМИЧЕСКИЕ ЦВЕТА (MATERIAL YOU) ----
        DynamicColorsSettingCard(
            settings = settings,
            isDarkTheme = isDarkTheme,
            primary = primary,
            onThemeChange = onThemeChange,
            onResetColors = { viewModel.resetAllColors(onColorsChange) }
        )

        // ---- 2.6 OLED-РЕЖИМ (чисто чёрный фон для тёмной темы) ----
        OledSettingCard(
            useOled = useOled,
            onOledChange = onOledChange,
            primary = primary
        )

        // ---- 3. ГРОМКОСТЬ ----
        VolumeSettingCard(
            volume = viewState.volume,
            onVolumeChange = { viewModel.setVolume(it) },
            primary = primary
        )

        // ---- 3.5 НАРАСТАНИЕ ГРОМКОСТИ ----
        VolumeRampSettingCard(
            settings = settings,
            primary = primary
        )

        // ---- 4. МЕЛОДИЯ ----
        RingtoneSettingCard(
            viewState = viewState,
            ringtoneLauncher = ringtoneLauncher,
            onPlayStop = { viewModel.playRingtone() },
            primary = primary
        )

        // ---- 5. ГРАФИК СМЕН + БРИГАДА (ОБЪЕДИНЁННЫЙ БЛОК) ----
        BrigadeAndScheduleCard(
            viewState = viewState,
            onScheduleTypeChange = { viewModel.setScheduleType(it) },
            onBrigadeChange = { viewModel.setBrigade(it) },
            primary = primary
        )

        // ---- 5.4 ВИДЖЕТ ----
        WidgetSettingCard(primary = primary)

        // ---- 5.5 ПРЕД-НАПОМИНАНИЕ О СМЕНЕ ----
        ShiftReminderSettingCard(
            settings = settings,
            scheduler = scheduler,
            primary = primary
        )

        // ---- 5.6 ПРОВЕРКА БУДИЛЬНИКА (перенесено из вкладки «Будильники») ----
        AlarmTestSettingCard(
            scheduler = scheduler,
            primary = primary
        )

        // ---- 5.7 АВТО-ТИШИНА (перенесено из вкладки «Будильники») ----
        AutoSilenceSettingCard(
            scheduler = scheduler,
            primary = primary
        )

        // ---- 6. О ПРИЛОЖЕНИИ ----
        AboutSettingCard(primary = primary)

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showPrimaryPicker) {
        ColorPickerDialog(
            title = "Выберите основной цвет",
            onColorSelected = { color ->
                viewModel.setPrimaryColor(color, onColorsChange)
                showPrimaryPicker = false
            },
            onDismiss = { showPrimaryPicker = false }
        )
    }
    if (showBackgroundPicker) {
        ColorPickerDialog(
            title = "Выберите цвет фона",
            onColorSelected = { color ->
                viewModel.setBackgroundColor(color, onColorsChange)
                showBackgroundPicker = false
            },
            onDismiss = { showBackgroundPicker = false }
        )
    }
    if (showSurfacePicker) {
        ColorPickerDialog(
            title = "Выберите цвет карточек",
            onColorSelected = { color ->
                viewModel.setSurfaceColor(color, onColorsChange)
                showSurfacePicker = false
            },
            onDismiss = { showSurfacePicker = false }
        )
    }

    if (showColorPicker && selectedShiftType != null) {
        ColorPickerDialog(
            title = "Цвет для ${selectedShiftType?.displayName}",
            onColorSelected = { color ->
                viewModel.setShiftColor(selectedShiftType!!, color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

// ===== УНИВЕРСАЛЬНЫЙ ДИАЛОГ ВЫБОРА ЦВЕТА =====
@Composable
fun ColorPickerDialog(
    title: String,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }

    val selectedColor = remember(hue, saturation, value) {
        hslToComposeColor(hue / 360f, saturation, value)
    }
    val hexColor = remember(selectedColor) {
        val r = (selectedColor.red * 255).toInt()
        val g = (selectedColor.green * 255).toInt()
        val b = (selectedColor.blue * 255).toInt()
        String.format("#%02X%02X%02X", r, g, b)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Предпросмотр
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(selectedColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        hexColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (value < 0.5f) Color.White else Color.Black
                    )
                }

                // Hue
                Text("Тон", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.HSV(hue, 1f, 1f),
                        activeTrackColor = Color.HSV(hue, 1f, 1f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                // Насыщенность
                Text("Насыщенность", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.HSV(hue, saturation, value),
                        activeTrackColor = Color.HSV(hue, saturation, value)
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                // Яркость
                Text("Яркость", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                // Быстрые оттенки чёрного/серого
                Text("Оттенки серого", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val darkShades = listOf(
                        0xFF000000, 0xFF0A0A0A, 0xFF121212, 0xFF1A1A1A,
                        0xFF222222, 0xFF2D2D2D, 0xFF333333, 0xFF424242,
                        0xFF555555, 0xFF666666, 0xFF888888, 0xFFAAAAAA
                    )
                    darkShades.forEach { argb ->
                        val shade = Color(argb)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(shade, RoundedCornerShape(5.dp))
                                .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                                .clickable {
                                    hue = 0f
                                    saturation = 0f
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
                                    value = hsv[2]
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor) }) {
                Text("Выбрать", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

fun hslToComposeColor(h: Float, s: Float, v: Float): Color {
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(h * 360f, s, v)))
}

fun Color.Companion.HSV(h: Float, s: Float, v: Float): Color {
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
}

// ===== ПРОВЕРКА БУДИЛЬНИКА (перенесено из вкладки «Будильники») =====
@Composable
private fun AlarmTestSettingCard(
    scheduler: AlarmScheduler,
    primary: Color
) {
    val context = LocalContext.current
    var needsPermission by remember { mutableStateOf(false) }
    // Отсчёт оставшихся секунд до сигнала. null = тест не запущен.
    var remainingSec by remember { mutableStateOf<Int?>(null) }

    // Пока идёт отсчёт, уменьшаем счётчик каждую секунду; по достижении 0 — завершаем тест.
    LaunchedEffect(remainingSec) {
        val remaining = remainingSec ?: return@LaunchedEffect
        if (remaining <= 0) {
            remainingSec = null  // вернуть кнопку в нормальное состояние
            return@LaunchedEffect
        }
        delay(1000)
        remainingSec = remaining - 1
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Text(text = "🧪", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Проверка будильника",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Убедиться, что звук и вибрация работают",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = remainingSec == null,
                    onClick = {
                        val ok = scheduler.scheduleTestAlarm(10)
                        if (ok) {
                            remainingSec = 10
                            needsPermission = false
                        } else {
                            needsPermission = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (remainingSec == null) "Проверить будильник"
                        else "Сигнал через $remainingSec сек...",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
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
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }) {
                        Text("Разрешить", color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ===== АВТО-ТИШИНА (перенесено из вкладки «Будильники») =====
@Composable
private fun AutoSilenceSettingCard(
    scheduler: AlarmScheduler,
    primary: Color
) {
    val context = LocalContext.current
    val settings = koinInject<SettingsManager>()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    var isEnabled by remember { mutableStateOf(settings.getAutoSilenceEnabled()) }
    var startTime by remember { mutableStateOf(settings.getAutoSilenceStart()) }
    var endTime by remember { mutableStateOf(settings.getAutoSilenceEnd()) }

    fun save(enabled: Boolean, start: String, end: String) {
        isEnabled = enabled
        startTime = start
        endTime = end
        settings.saveAutoSilenceEnabled(enabled)
        settings.saveAutoSilenceStart(start)
        settings.saveAutoSilenceEnd(end)
        scheduler.updateAutoSilenceAlarms(enabled, start, end)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Text(text = "🌙", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Авто-тишина",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Автоматически после ночной смены",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
                        if (checked
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

            // Проверка автотишины: тестовый режим — включить беззвучный режим
            // через 5 сек, выключить на 10-й секунде (независимо от графика).
            var testNeedsPermission by remember { mutableStateOf(false) }
            // Отсчёт оставшихся секунд до теста. null = тест не запущен.
            var testRemainingSec by remember { mutableStateOf<Int?>(null) }

            // Пока идёт отсчёт, уменьшаем счётчик каждую секунду; по достижении 0 — завершаем тест.
            LaunchedEffect(testRemainingSec) {
                val remaining = testRemainingSec ?: return@LaunchedEffect
                if (remaining <= 0) {
                    testRemainingSec = null  // вернуть кнопку в нормальное состояние
                    return@LaunchedEffect
                }
                delay(1000)
                testRemainingSec = remaining - 1
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = testRemainingSec == null,
                    onClick = {
                        // старт тишины через 5 сек, выключение/возврат на 10-й секунде.
                        val ok = scheduler.scheduleTestSilence(5, 5)
                        if (ok) {
                            testRemainingSec = 10
                            testNeedsPermission = false
                        } else {
                            testNeedsPermission = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    val label = when {
                        testRemainingSec == null -> "Проверить автотишину"
                        testRemainingSec!! > 5 -> "Тишина через ${testRemainingSec} сек..."
                        else -> "Возврат через ${testRemainingSec} сек..."
                    }
                    Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (testNeedsPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Точные будильники отключены. Разрешите их, чтобы проверить автотишину.",
                        fontSize = 12.sp,
                        color = Color(0xFFFF5252),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }) {
                        Text("Разрешить", color = primary, fontWeight = FontWeight.Bold)
                    }
                }
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

