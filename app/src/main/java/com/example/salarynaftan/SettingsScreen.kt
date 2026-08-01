package com.example.salarynaftan

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
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
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onColorsChange: (Color, Color, Color) -> Unit,
    currentPrimaryColor: Color,
    currentBackgroundColor: Color,
    currentSurfaceColor: Color
) {
    val context = LocalContext.current

    // ===== ВСЁ ЧЕРЕЗ KOIN =====
    val settings = koinInject<SettingsManager>()
    val colorSettings = koinInject<ColorSettingsManager>()
    val scheduler = koinInject<AlarmScheduler>()

    var volume by remember { mutableFloatStateOf(settings.getVolume()) }
    var ringtoneName by remember { mutableStateOf(settings.getRingtoneName()) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // Безопасное освобождение — предотвращает двойной release()
    fun releasePlayer() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
    }

    val currentMediaPlayer by rememberUpdatedState(mediaPlayer)
    DisposableEffect(Unit) {
        onDispose {
            try { currentMediaPlayer?.release() } catch (_: Exception) {}
        }
    }

    var brigade by remember { mutableIntStateOf(settings.getBrigade()) }
    var morningColor by remember { mutableStateOf(colorSettings.getMorningColor()) }
    var dayColor by remember { mutableStateOf(colorSettings.getDayColor()) }
    var nightColor by remember { mutableStateOf(colorSettings.getNightColor()) }
    var offColor by remember { mutableStateOf(colorSettings.getOffColor()) }

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
                settings.saveRingtoneUri(uri.toString())
                ringtoneName = settings.getRingtoneName()
            }
        }
    }

    fun resetAllColorsToDefault() {
        val defaultMorning = Color(0xFFFEE45B)
        val defaultDay = Color(0xFFA2D39C)
        val defaultNight = Color(0xFF4F6D91)
        val defaultOff = Color(0xFFF8EDF3)

        val defaultPrimary = if (isDarkTheme) Color(0xFF00E676) else Color(0xFF00A859)
        val defaultBg = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFFFFFF)
        val defaultSurface = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)

        settings.savePrimaryColor(defaultPrimary)
        settings.saveBackgroundColor(defaultBg)
        settings.saveSurfaceColor(defaultSurface)

        colorSettings.saveMorningColor(defaultMorning)
        colorSettings.saveDayColor(defaultDay)
        colorSettings.saveNightColor(defaultNight)
        colorSettings.saveOffColor(defaultOff)

        morningColor = defaultMorning
        dayColor = defaultDay
        nightColor = defaultNight
        offColor = defaultOff

        onColorsChange(defaultPrimary, defaultBg, defaultSurface)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "⚙️ Настройки",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // ---- 1. ТЕМА ----
        CompactSettingCard(
            icon = if (isDarkTheme) "🌙" else "☀️",
            title = "Тёмная тема",
            description = if (isDarkTheme) "Вкл" else "Выкл"
        ) {
            Switch(
                checked = isDarkTheme,
                onCheckedChange = { checked ->
                    // onThemeChange уже вызывает saveBackgroundColor/saveSurfaceColor
                    // и recreate() внутри MainActivity — второй вызов onColorsChange
                    // не нужен, т.к. он вызовет повторное recreate().
                    onThemeChange(checked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E676),
                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(28.dp)
            )
        }

        // ---- 2. ВСЕ НАСТРОЙКИ ЦВЕТОВ ----
        CompactSettingCard(
            icon = "🎨",
            title = "Оформление",
            description = "Цвета приложения"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // --- Пресеты тем ---
                Text("Пресеты", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                data class ColorPreset(
                    val name: String, val primary: Color, val background: Color, val surface: Color,
                    val morning: Color, val day: Color, val night: Color, val off: Color
                )
                val presets = listOf(
                    ColorPreset("🟢 Стандарт", Color(0xFF00E676), Color(0xFF121212), Color(0xFF1E1E1E),
                        Color(0xFFFEE45B), Color(0xFFA2D39C), Color(0xFF4F6D91), Color(0xFFF8EDF3)),
                    ColorPreset("🔵 Океан", Color(0xFF03A9F4), Color(0xFF0D1B2A), Color(0xFF1B2838),
                        Color(0xFFFFCC80), Color(0xFF80DEEA), Color(0xFF5C6BC0), Color(0xFFE8EAF6)),
                    ColorPreset("🟣 Фиолет", Color(0xFFBB86FC), Color(0xFF1A1A2E), Color(0xFF16213E),
                        Color(0xFFFFE082), Color(0xFFCE93D8), Color(0xFF7986CB), Color(0xFFEDE7F6)),
                    ColorPreset("🔴 Огонь", Color(0xFFFF6B6B), Color(0xFF1A1A1A), Color(0xFF2D2D2D),
                        Color(0xFFFFF59D), Color(0xFFFFAB91), Color(0xFFEF9A9A), Color(0xFFFCE4EC)),
                    ColorPreset("🌿 Лес", Color(0xFF4CAF50), Color(0xFF1B2D1B), Color(0xFF2E3E2E),
                        Color(0xFFFFF9C4), Color(0xFFA5D6A7), Color(0xFF66BB6A), Color(0xFFE8F5E9)),
                    ColorPreset("🌙 Тёмная", Color(0xFFBB86FC), Color(0xFF000000), Color(0xFF121212),
                        Color(0xFF37474F), Color(0xFF455A64), Color(0xFF37474F), Color(0xFF263238)),
                )
                presets.chunked(3).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            Button(
                                onClick = {
                                    // Сохраняем смены
                                    colorSettings.saveMorningColor(preset.morning)
                                    colorSettings.saveDayColor(preset.day)
                                    colorSettings.saveNightColor(preset.night)
                                    colorSettings.saveOffColor(preset.off)
                                    morningColor = preset.morning
                                    dayColor = preset.day
                                    nightColor = preset.night
                                    offColor = preset.off
                                    // Сохраняем тему (все пресеты тёмные) и применяем
                                    if (!isDarkTheme) {
                                        settings.saveTheme(true)
                                    }
                                    // Сохраняем основные цвета и пересоздаём Activity
                                    onColorsChange(preset.primary, preset.background, preset.surface)
                                },
                                modifier = Modifier.weight(1f).height(30.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = preset.primary.copy(alpha = 0.2f)),
                                border = BorderStroke(0.5.dp, preset.primary),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                            ) {
                                Text(preset.name, fontSize = 9.sp, color = preset.primary, maxLines = 1)
                            }
                        }
                        repeat(3 - rowPresets.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

                CompactColorRow(
                    label = "Основной",
                    color = currentPrimaryColor,
                    onClick = { showPrimaryPicker = true }
                )
                CompactColorRow(
                    label = "Фон",
                    color = currentBackgroundColor,
                    onClick = { showBackgroundPicker = true }
                )
                CompactColorRow(
                    label = "Карточки",
                    color = currentSurfaceColor,
                    onClick = { showSurfacePicker = true }
                )
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                Text("Смены", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                listOf(
                    "🌅 Утро" to morningColor to ShiftType.MORNING,
                    "☀️ День" to dayColor to ShiftType.DAY,
                    "🌙 Ночь" to nightColor to ShiftType.NIGHT,
                    "📅 Выходной" to offColor to ShiftType.OFF
                ).forEach { (pair, type) ->
                    val (label, color) = pair
                    CompactColorRow(
                        label = label,
                        color = color,
                        onClick = {
                            selectedShiftType = type
                            showColorPicker = true
                        }
                    )
                }

                Button(
                    onClick = { resetAllColorsToDefault() },
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Сбросить все цвета", color = Color(0xFFFF5252), fontSize = 11.sp)
                }
            }
        }

        // ---- 3. ГРОМКОСТЬ ----
        CompactSettingCard(
            icon = "🔊",
            title = "Громкость",
            description = "${(volume * 100).toInt()}%"
        ) {
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    settings.saveVolume(it)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(24.dp)
            )
        }

        // ---- 4. МЕЛОДИЯ ----
        CompactSettingCard(
            icon = "🎵",
            title = "Мелодия",
            description = ringtoneName
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            settings.getRingtoneUri()?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                            }
                        }
                        ringtoneLauncher.launch(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Выбрать", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        if (isPlaying) {
                            releasePlayer()
                            return@OutlinedButton
                        }
                        releasePlayer()
                        val uri = settings.getRingtoneUri()
                        if (uri != null) {
                            try {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(context, uri)
                                    setOnErrorListener { _, _, _ ->
                                        releasePlayer()
                                        true
                                    }
                                    prepare()
                                    setVolume(volume, volume)
                                    setOnCompletionListener { _ ->
                                        releasePlayer()
                                    }
                                    start()
                                }
                                isPlaying = true
                            } catch (_: Exception) {
                                releasePlayer()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (isPlaying) "⏹" else "▶", fontSize = 16.sp)
                }
            }
        }

        // ---- 5. БРИГАДА ----
        CompactSettingCard(
            icon = "👥",
            title = "Бригада",
            description = "Активна: $brigade"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { num ->
                    val selected = brigade == num
                    FilterChip(
                        selected = selected,
                        onClick = {
                            brigade = num
                            settings.setBrigade(num)
                            scheduler.rescheduleAllAlarmsForBrigade(num)
                        },
                        label = { Text(num.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.DarkGray,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(28.dp)
                    )
                }
            }
        }

        // ---- 6. О ПРИЛОЖЕНИИ ----
        CompactSettingCard(
            icon = "ℹ️",
            title = "О приложении",
            description = "Версия ${getAppVersion(context)}"
        ) {
            var showAbout by remember { mutableStateOf(false) }
            Button(
                onClick = { showAbout = true },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Открыть", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

    // ---- ДИАЛОГИ ВЫБОРА ЦВЕТА ----
    val colorPalette = listOf(
        Color(0xFF00E676), Color(0xFFFF6B6B), Color(0xFFFF9F43),
        Color(0xFF6C5CE7), Color(0xFF00CEC9), Color(0xFFFFF200),
        Color(0xFF00B894), Color(0xFF0984E3), Color(0xFFFD79A8),
        Color(0xFF2D3436),
        Color(0xFFFEE45B), Color(0xFFA2D39C), Color(0xFF4F6D91),
        Color(0xFFF8EDF3), Color(0xFFFFB3B3), Color(0xFFB3D9FF),
        Color(0xFFD4F0C0), Color(0xFFFFE0B2), Color(0xFFE1BEE7),
        Color(0xFFB2EBF2),
        Color(0xFFFF8A65), Color(0xFFA1887F), Color(0xFF90A4AE),
        Color(0xFFFFCC80)
    )

    if (showPrimaryPicker) {
        ColorPickerDialog(
            title = "Выберите основной цвет",
            colors = colorPalette,
            onColorSelected = { color ->
                onColorsChange(color, currentBackgroundColor, currentSurfaceColor)
                showPrimaryPicker = false
            },
            onDismiss = { showPrimaryPicker = false }
        )
    }
    if (showBackgroundPicker) {
        ColorPickerDialog(
            title = "Выберите цвет фона",
            colors = colorPalette,
            onColorSelected = { color ->
                onColorsChange(currentPrimaryColor, color, currentSurfaceColor)
                showBackgroundPicker = false
            },
            onDismiss = { showBackgroundPicker = false }
        )
    }
    if (showSurfacePicker) {
        ColorPickerDialog(
            title = "Выберите цвет карточек",
            colors = colorPalette,
            onColorSelected = { color ->
                onColorsChange(currentPrimaryColor, currentBackgroundColor, color)
                showSurfacePicker = false
            },
            onDismiss = { showSurfacePicker = false }
        )
    }

    if (showColorPicker && selectedShiftType != null) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Цвет для ${selectedShiftType?.displayName}", fontSize = 14.sp) },
            text = {
                Column {
                    val shiftColors = listOf(
                        Color(0xFFFEE45B), Color(0xFFA2D39C), Color(0xFF4F6D91),
                        Color(0xFFF8EDF3), Color(0xFFFF6B6B), Color(0xFFFF9F43),
                        Color(0xFF6C5CE7), Color(0xFF00CEC9), Color(0xFFFFF200),
                        Color(0xFF00B894), Color(0xFFFFB3B3), Color(0xFFB3D9FF),
                        Color(0xFFD4F0C0), Color(0xFFFFE0B2), Color(0xFFE1BEE7),
                        Color(0xFFB2EBF2), Color(0xFFFF8A65), Color(0xFFA1887F),
                        Color(0xFF90A4AE), Color(0xFFFFCC80)
                    )
                    shiftColors.chunked(5).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(color, RoundedCornerShape(50))
                                        .clickable {
                                            when (selectedShiftType) {
                                                ShiftType.MORNING -> {
                                                    colorSettings.saveMorningColor(color)
                                                    morningColor = color
                                                }
                                                ShiftType.DAY -> {
                                                    colorSettings.saveDayColor(color)
                                                    dayColor = color
                                                }
                                                ShiftType.NIGHT -> {
                                                    colorSettings.saveNightColor(color)
                                                    nightColor = color
                                                }
                                                ShiftType.OFF -> {
                                                    colorSettings.saveOffColor(color)
                                                    offColor = color
                                                }
                                                else -> {}
                                            }
                                            showColorPicker = false
                                        }
                                )
                            }
                            repeat(5 - rowColors.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        )
    }
}

// ===== УНИВЕРСАЛЬНЫЙ ДИАЛОГ ВЫБОРА ЦВЕТА =====
@Composable
fun ColorPickerDialog(
    title: String,
    colors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 14.sp) },
        text = {
            Column {
                colors.chunked(5).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, RoundedCornerShape(50))
                                    .clickable { onColorSelected(color) }
                            )
                        }
                        repeat(5 - rowColors.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    )
}

// ===== КОМПАКТНАЯ КАРТОЧКА =====
@Composable
fun CompactSettingCard(
    icon: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.DarkGray),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(icon, fontSize = 14.sp)
                Column {
                    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(description, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                }
            }
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f), thickness = 0.5.dp)
            content()
        }
    }
}

// ===== КОМПАКТНАЯ СТРОКА ЦВЕТА =====
@Composable
fun CompactColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp)
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color, RoundedCornerShape(3.dp))
                .border(0.5.dp, Color.DarkGray, RoundedCornerShape(3.dp))
        )
    }
}

