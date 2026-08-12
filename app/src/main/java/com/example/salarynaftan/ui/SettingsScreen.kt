package com.example.salarynaftan.ui
import com.example.salarynaftan.*
import com.example.salarynaftan.R

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
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
            onScheduleTypeChange = { viewModel.setScheduleType(it, scheduler) },
            onBrigadeChange = { viewModel.setBrigade(it, scheduler) },
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

