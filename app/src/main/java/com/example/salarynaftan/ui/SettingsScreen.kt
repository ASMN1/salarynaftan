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
    onUiScaleChange: (Float) -> Unit
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

        // ---- 1. ТЕМА ----
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
                    onCheckedChange = { viewModel.setTheme(it, onThemeChange) },
                    trackColor = primary
                )
            }
        }

        // ---- 2. ВСЕ НАСТРОЙКИ ЦВЕТОВ ----
        PremiumSettingCard(
            icon = "🎨",
            title = "Оформление",
            description = "Цвета приложения"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumDivider()

                PremiumColorRow(
                    label = "Основной",
                    color = currentPrimaryColor,
                    onClick = { showPrimaryPicker = true }
                )
                PremiumColorRow(
                    label = "Фон",
                    color = currentBackgroundColor,
                    onClick = { showBackgroundPicker = true }
                )
                PremiumColorRow(
                    label = "Карточки",
                    color = currentSurfaceColor,
                    onClick = { showSurfacePicker = true }
                )
                PremiumDivider()
                Text("Смены", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
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
                        onClick = {
                            selectedShiftType = type
                            showColorPicker = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.resetAllColors(onColorsChange) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.12f),
                        contentColor = Color(0xFFFF5252)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Сбросить все цвета", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        // ---- 2.4 МАСШТАБ ИНТЕРФЕЙСА ----
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

        // ---- 2.5 ДИНАМИЧЕСКИЕ ЦВЕТА (MATERIAL YOU) ----
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
                            viewModel.resetAllColors(onColorsChange)
                        }
                        onThemeChange(isDarkTheme) // recreate
                    },
                    trackColor = primary
                )
            }
        }

        // ---- 3. ГРОМКОСТЬ ----
        PremiumSettingCard(
            icon = "🔊",
            title = "Громкость",
            description = "${(viewState.volume * 100).toInt()}%"
        ) {
            Slider(
                value = viewState.volume,
                onValueChange = { viewModel.setVolume(it) },
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

        // ---- 3.5 НАРАСТАНИЕ ГРОМКОСТИ ----
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

        // ---- 4. МЕЛОДИЯ ----
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
                    onClick = { viewModel.playRingtone() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (viewState.isPlaying) "⏹  Стоп" else "▶  Слушать", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- 5. БРИГАДА ----
        PremiumSettingCard(
            icon = "👥",
            title = "Бригада",
            description = "Активна: ${viewState.brigade}"
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                (1..5).forEach { num ->
                    val selected = viewState.brigade == num
                    Surface(
                        onClick = { viewModel.setBrigade(num, scheduler) },
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
        }

        // ---- 6. О ПРИЛОЖЕНИИ ----
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

// ===== КАРТОЧКА НАСТРОЙКИ =====
@Composable
private fun PremiumSettingCard(
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
private fun PremiumColorRow(label: String, color: Color, onClick: () -> Unit) {
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
