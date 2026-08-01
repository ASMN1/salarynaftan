package com.example.salarynaftan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import org.koin.compose.koinInject



// ==========================================
// ЭКРАН: ЗАРПЛАТА (с ViewModel через Koin)
// ==========================================

@Composable
fun SalaryCalculatorScreen(
    isDarkTheme: Boolean,
    viewModel: SalaryCalculatorViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val historyManager = koinInject<HistoryManager>()
    var historyList by remember { mutableStateOf(historyManager.getRecords()) }

    // ===== OCR СКАНЕР (состояние на верхнем уровне) =====
    val ocrScanner = remember { OcrSalaryScanner() }
    val coroutineScope = rememberCoroutineScope()
    var ocrResult by remember { mutableStateOf<OcrSalaryScanner.OcrResult?>(null) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var ocrLoading by remember { mutableStateOf(false) }
    var ocrImageUri by remember { mutableStateOf<Uri?>(null) }
    var showOcrMenu by remember { mutableStateOf(false) }

    // Лаунчер галереи
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            ocrImageUri = it
            ocrLoading = true
            coroutineScope.launch {
                val rawText = ocrScanner.recognizeText(context, it)
                ocrResult = ocrScanner.parseSalaryFields(rawText)
                ocrLoading = false
                showOcrDialog = true
            }
        }
    }

    // Лаунчер камеры
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && ocrImageUri != null) {
            ocrLoading = true
            coroutineScope.launch {
                val rawText = ocrScanner.recognizeText(context, ocrImageUri!!)
                ocrResult = ocrScanner.parseSalaryFields(rawText)
                ocrLoading = false
                showOcrDialog = true
            }
        }
    }

    // Лаунчер разрешения камеры
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val photoDir = java.io.File(context.cacheDir, "exports")
                    photoDir.mkdirs()
                    val photoFile = java.io.File(photoDir, "ocr_photo_${System.currentTimeMillis()}.jpg")
                photoFile.createNewFile()
                val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                ocrImageUri = photoUri
                cameraLauncher.launch(photoUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Заголовок
        Text(
            text = "💰 Расчёт зарплаты",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // ===== ВЫБОР МЕСЯЦА =====
        MonthSelector(
            selectedMonthIndex = uiState.selectedMonthIndex,
            onMonthSelected = { viewModel.selectMonth(it) }
        )

        // ===== СЕКЦИЯ 1: РАБОЧЕЕ ВРЕМЯ =====
        ExpandableSection(
            title = "⏱ Рабочее время",
            initiallyExpanded = true
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = uiState.normHours,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.NORM_HOURS, it) },
                    label = "Норма",
                    icon = "🕐",
                    modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.factHours,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.FACT_HOURS, it) },
                    label = "Факт",
                    icon = "✅",
                    modifier = Modifier.weight(1f)
                )
            }
            // Прогресс-бар выполнения нормы
            val normVal = uiState.normHours.toDoubleOrNull() ?: 0.0
            val factVal = uiState.factHours.toDoubleOrNull() ?: 0.0
            val progress = if (normVal > 0) (factVal / normVal).coerceIn(0.0, 1.5) else 0.0
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (progress >= 1f) Color(0xFF00E676) else Color(0xFFFFA726),
                trackColor = Color.DarkGray
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Выполнение нормы: ${(progress * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                InputFieldWithText(
                    value = uiState.childrenCountInput,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.CHILDREN_COUNT, it) },
                    label = "Детей",
                    icon = "👶",
                    modifier = Modifier.width(80.dp)
                )
            }
            // Кнопка автозаполнения
            OutlinedButton(
                onClick = { viewModel.autoFillFromSchedule() },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("📥 Заполнить часы и смены из графика", fontSize = 12.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
            }

            // ===== OCR КНОПКА =====
            OutlinedButton(
                onClick = { showOcrMenu = true },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, Color(0xFFFF9800))
            ) {
                if (ocrLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFFF9800),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Распознавание...", fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                } else {
                    Text("📷 Сканировать ведомость (OCR)", fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                }
            }

            // Меню выбора источника
            DropdownMenu(
                expanded = showOcrMenu,
                onDismissRequest = { showOcrMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("📷 Камера", fontSize = 13.sp) },
                    onClick = {
                        showOcrMenu = false
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                )
                DropdownMenuItem(
                    text = { Text("🖼️ Галерея", fontSize = 13.sp) },
                    onClick = {
                        showOcrMenu = false
                        galleryLauncher.launch("image/*")
                    }
                )
            }
        }

        // ===== СЕКЦИЯ 2: СМЕНЫ И ДОПЛАТЫ =====
        ExpandableSection(
            title = "🌙 Смены и доплаты",
            initiallyExpanded = false
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = uiState.nightShifts,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.NIGHT_SHIFTS, it) },
                    label = "Ночные смены",
                    icon = "🌙",
                    modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.s4Shifts,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.S4_SHIFTS, it) },
                    label = "Смены «с 4»",
                    icon = "⏰",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = uiState.prazdnHours,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.PRAZDN_HOURS, it) },
                    label = "Праздн. часы",
                    icon = "🎉",
                    modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.advanceShifts,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ADVANCE_SHIFTS, it) },
                    label = "Смен аванса (1-15)",
                    icon = "💳",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ===== СЕКЦИЯ 3: ПРЕМИИ И ВЫПЛАТЫ =====
        ExpandableSection(
            title = "⭐ Премии и дополнительные выплаты",
            initiallyExpanded = false
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = uiState.zaOtsutstvuushego,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ZA_OTSUTSTVUUSHEGO, it) },
                    label = "За отсутств. (руб)",
                    icon = "👤",
                    modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.kvartalka,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, it) },
                    label = "Кварталка (руб)",
                    icon = "💰",
                    modifier = Modifier.weight(1f)
                )
            }
            InputFieldWithText(
                value = uiState.mmDetiCountInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.MM_DETI, it) },
                label = "МП на детей до 3л (баз.вел.)",
                icon = "👪",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ===== СЕКЦИЯ 4: УДЕРЖАНИЯ =====
        ExpandableSection(
            title = "🔻 Удержания и невыходы",
            initiallyExpanded = false,
            danger = true
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = uiState.gazetaInput,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.GAZETA, it) },
                    label = "Газета (руб)",
                    icon = "📰",
                    modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.pozhertvovanjaInput,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.POZHERTVOVANJA, it) },
                    label = "Пожертв. (руб)",
                    icon = "❤️",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = uiState.subbotnikInput,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.SUBBOTNIK, it) },
                    label = "Субботник (руб)",
                    icon = "🧹",
                    modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.zaSvoySchetInput,
                    onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ZA_SVOY_SCHET, it) },
                    label = "За свой счет (смен)",
                    icon = "🚫",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ===== ОШИБКА =====
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = Color(0xFFFF5252),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ===== КНОПКА РАССЧИТАТЬ =====
        Button(
            onClick = { viewModel.performCalculation() },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("🧮 РАССЧИТАТЬ", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // ===== РЕЗУЛЬТАТЫ =====
        AnimatedVisibility(
            visible = uiState.showResults && uiState.calculationResult != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val result = uiState.calculationResult!!
            ResultCard(
                isDarkTheme = isDarkTheme,
                resOkladReal = result.okladReal,
                resStazh = result.stazh,
                resVrednost = result.vrednost,
                resNightHours = result.nightHours,
                resNochPay = result.nochPay,
                resPrazdn = result.prazdn,
                resPrem = result.prem,
                resMmDeti = result.mmDeti,
                resSumBeforePension = result.sumBeforePension,
                resPension = result.pension,
                resDirty = result.dirty,
                resFszn = result.fszn,
                resProf = result.prof,
                resChildrenDeduction = result.childrenDeduction,
                resPodohodnyBase = result.podohodnyBase,
                resPodohodny = result.podohodny,
                resAvans = result.avans,
                resTotalClean = result.totalClean,
                resCleanToPay = result.cleanToPay,
                effectiveFactText = uiState.effectiveFactText,
                normHours = uiState.normHours,
                factHours = uiState.factHours,
                childrenCountInput = uiState.childrenCountInput,
                mmDetiCountInput = uiState.mmDetiCountInput,
                zaOtsutstvuushego = uiState.zaOtsutstvuushego,
                kvartalka = uiState.kvartalka,
                gazetaInput = uiState.gazetaInput,
                pozhertvovanjaInput = uiState.pozhertvovanjaInput,
                subbotnikInput = uiState.subbotnikInput,
                selectedMonthIndex = uiState.selectedMonthIndex,
                months = MonthlyNorms.list,
                historyManager = historyManager,
                onHistorySaved = {
                    historyList = historyManager.getRecords()
                }
            )
        }

        // ===== ИСТОРИЯ =====
        HistoryCard(
            historyList = historyList,
            isDarkTheme = isDarkTheme,
            historyManager = historyManager,
            onHistoryChanged = { historyList = historyManager.getRecords() }
        )
    }

    // ===== ДИАЛОГ РЕЗУЛЬТАТОВ OCR =====
    if (showOcrDialog && ocrResult != null) {
        val result = ocrResult!!
        var selectedFields by remember { mutableStateOf(HashMap<String, String>()) }

        AlertDialog(
            onDismissRequest = { showOcrDialog = false },
            title = {
                Text("📷 Результаты сканирования", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result.recognizedNumbers.isEmpty()) {
                        Text(
                            "Не удалось распознать данные. Попробуйте другое фото.",
                            fontSize = 12.sp,
                            color = Color(0xFFFF5252)
                        )
                    } else {
                        Text("Найденные значения:", fontSize = 11.sp, color = Color.Gray)
                        result.recognizedNumbers.forEach { (label, value) ->
                            var isSelected by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSelected = !isSelected
                                        if (isSelected) selectedFields[label] = value
                                        else selectedFields.remove(label)
                                    }
                                    .background(
                                        if (isSelected) Color(0x3300E676) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            isSelected = it
                                            if (it) selectedFields[label] = value
                                            else selectedFields.remove(label)
                                        },
                                        modifier = Modifier.size(20.dp),
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            }
                        }
                    }

                    // Показать распознанный текст (по клику)
                    var showRawText by remember { mutableStateOf(false) }
                    TextButton(onClick = { showRawText = !showRawText }) {
                        Text(
                            if (showRawText) "Скрыть текст ▲" else "Показать распознанный текст ▼",
                            fontSize = 10.sp, color = Color.Gray
                        )
                    }
                    if (showRawText) {
                        Text(
                            result.rawText.take(1000),
                            fontSize = 9.sp,
                            color = Color.Gray,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Применяем выбранные поля, либо все распознанные если ничего не выбрано
                        val fieldsToApply: Map<String, String> = selectedFields.ifEmpty {
                            result.recognizedNumbers.associate { it.first to it.second }
                        }
                        fieldsToApply.forEach { (label, value) ->
                            when {
                                label.contains("Норма") -> viewModel.updateField(SalaryCalculatorViewModel.SalaryField.NORM_HOURS, value)
                                label.contains("Факт") -> viewModel.updateField(SalaryCalculatorViewModel.SalaryField.FACT_HOURS, value)
                                label.contains("Ночн") -> viewModel.updateField(SalaryCalculatorViewModel.SalaryField.NIGHT_SHIFTS, value)
                                label.contains("с 4") || label.contains("С 4") || label.contains("Дневн") -> viewModel.updateField(SalaryCalculatorViewModel.SalaryField.S4_SHIFTS, value)
                                label.contains("Аванс") -> viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ADVANCE_SHIFTS, value)
                                label.contains("Праздн") -> viewModel.updateField(SalaryCalculatorViewModel.SalaryField.PRAZDN_HOURS, value)
                            }
                        }
                        showOcrDialog = false
                    },
                    enabled = selectedFields.isNotEmpty() || result.recognizedNumbers.isNotEmpty()
                ) {
                    Text(
                        if (selectedFields.isNotEmpty()) "Применить (${selectedFields.size})" else "Применить все",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrDialog = false }) {
                    Text("Отмена", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }
}

// ==========================================
// ВСПОМОГАТЕЛЬНЫЕ UI КОМПОНЕНТЫ
// ==========================================

@Composable
fun MonthSelector(
    selectedMonthIndex: Int,
    onMonthSelected: (Int) -> Unit
) {
    val months = MonthlyNorms.list
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.DarkGray),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("📅 Месяц расчёта", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = months[selectedMonthIndex].name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { Text("▼", color = Color(0xFF00E676), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    shape = RoundedCornerShape(6.dp),
                    singleLine = true
                )
                Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    months.forEachIndexed { index, monthData ->
                        DropdownMenuItem(
                            text = { Text(monthData.name, fontSize = 13.sp) },
                            onClick = {
                                onMonthSelected(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            if (danger) Color(0xAAFF5252) else Color.DarkGray
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (danger) "⚠️" else "📌", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (danger) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (expanded) "▲" else "▼",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun InputFieldWithText(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        leadingIcon = { Text(icon, fontSize = 14.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
        shape = RoundedCornerShape(6.dp)
    )
}

// ==========================================
// КАРТОЧКА РЕЗУЛЬТАТОВ
// ==========================================

@Composable
fun ResultCard(
    isDarkTheme: Boolean,
    resOkladReal: Double,
    resStazh: Double,
    resVrednost: Double,
    resNightHours: Double,
    resNochPay: Double,
    resPrazdn: Double,
    resPrem: Double,
    resMmDeti: Double,
    resSumBeforePension: Double,
    resPension: Double,
    resDirty: Double,
    resFszn: Double,
    resProf: Double,
    resChildrenDeduction: Double,
    resPodohodnyBase: Double,
    resPodohodny: Double,
    resAvans: Double,
    resTotalClean: Double,
    resCleanToPay: Double,
    effectiveFactText: String,
    normHours: String,
    factHours: String,
    childrenCountInput: String,
    mmDetiCountInput: String,
    zaOtsutstvuushego: String,
    kvartalka: String,
    gazetaInput: String,
    pozhertvovanjaInput: String,
    subbotnikInput: String,
    selectedMonthIndex: Int,
    months: List<MonthData>,
    historyManager: HistoryManager,
    onHistorySaved: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF00E676)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF003B22), RoundedCornerShape(6.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("К ВЫПЛАТЕ:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${String.format(Locale.US, "%.2f", resCleanToPay)} руб", color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            Text("📈 Начисления", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
            ResultRow("Оклад расчётный", resOkladReal)
            ResultRow("Надбавка за стаж (25%)", resStazh)
            ResultRow("Доплата за вредность", resVrednost)
            ResultRow("Ночные часы (${resNightHours.toInt()} ч)", resNochPay)
            if (resPrazdn > 0) ResultRow("Праздничные часы", resPrazdn)
            ResultRow("Премия (за прошлый мес.)", resPrem)
            if (parseNonNegative(zaOtsutstvuushego) > 0) ResultRow("За отсутств. сотрудника", parseNonNegative(zaOtsutstvuushego))
            if (parseNonNegative(kvartalka) > 0) ResultRow("Квартальная премия", parseNonNegative(kvartalka))
            if (resMmDeti > 0) ResultRow("МП на детей до 3л (${displayInt(mmDetiCountInput)} баз.вел.)", resMmDeti)
            ResultRow("ППС (6%)", resPension)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ВСЕГО НАЧИСЛЕНО:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${String.format(Locale.US, "%.2f", resDirty)} руб", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            Text("🧾 Налоговая база", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA726))
            ResultRow("Общая сумма", resDirty)
            ResultRowGray("Налоговый вычет на детей (${displayInt(childrenCountInput)} дет.)", -resChildrenDeduction)
            if (resMmDeti > 0) ResultRowGray("МП на детей (не облагается)", -resMmDeti)
            ResultRowGray("Облагаемый доход", resPodohodnyBase)

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            Text("🔻 Удержания", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
            ResultRow("ФСЗН (1%)", resFszn)
            ResultRow("Профсоюз (1%)", resProf)
            ResultRow("Подоходный налог (13%)", resPodohodny)
            if (parseNonNegative(gazetaInput) > 0) ResultRow("Газета", parseNonNegative(gazetaInput))
            if (parseNonNegative(pozhertvovanjaInput) > 0) ResultRow("Пожертвования", parseNonNegative(pozhertvovanjaInput))
            if (parseNonNegative(subbotnikInput) > 0) ResultRow("Субботник", parseNonNegative(subbotnikInput))

            val totalUderzhano = resFszn + resProf + resPodohodny +
                    parseNonNegative(gazetaInput) + parseNonNegative(pozhertvovanjaInput) + parseNonNegative(subbotnikInput)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ВСЕГО УДЕРЖАНО:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
                Text("${String.format(Locale.US, "%.2f", totalUderzhano)} руб", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
            }

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            Text("💰 Итоги", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
            ResultRow("Всего чистыми за месяц", resTotalClean)
            ResultRow("Выплачено в аванс", -resAvans)

            Button(
                onClick = {
                    historyManager.saveRecord(
                        selectedMonthIndex,
                        months[selectedMonthIndex].name,
                        resTotalClean,
                        resCleanToPay,
                        resAvans
                    )
                    onHistorySaved()
                },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E676)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("💾 Сохранить в историю", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            // Кнопка экспорта расчёта в PDF
            val context = LocalContext.current
            Button(
                onClick = {
                    try {
                        val file = SalaryPdfExporter.createPdf(
                            context = context,
                            monthName = months[selectedMonthIndex].name,
                            state = SalaryCalculatorViewModel.SalaryUiState(
                                normHours = normHours,
                                factHours = factHours,
                                nightShifts = "",
                                s4Shifts = "",
                                advanceShifts = "",
                                prazdnHours = "0",
                                zaOtsutstvuushego = zaOtsutstvuushego,
                                kvartalka = kvartalka,
                                gazetaInput = gazetaInput,
                                pozhertvovanjaInput = pozhertvovanjaInput,
                                subbotnikInput = subbotnikInput,
                                zaSvoySchetInput = "0",
                                mmDetiCountInput = mmDetiCountInput,
                                childrenCountInput = childrenCountInput
                            ),
                            result = SalaryCalculatorViewModel.CalculationResultWithError(
                                okladReal = resOkladReal, stazh = resStazh, vrednost = resVrednost,
                                nightHours = resNightHours, nochPay = resNochPay, prazdn = resPrazdn,
                                prem = resPrem, mmDeti = resMmDeti, sumBeforePension = resSumBeforePension,
                                pension = resPension, dirty = resDirty, fszn = resFszn,
                                prof = resProf, childrenDeduction = resChildrenDeduction,
                                podohodnyBase = resPodohodnyBase, podohodny = resPodohodny,
                                avans = resAvans, totalClean = resTotalClean, cleanToPay = resCleanToPay,
                                effectiveFactText = effectiveFactText
                            )
                        )
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(viewIntent, "Открыть PDF"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("📄 Экспорт расчёта в PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        Text("${String.format(Locale.US, "%.2f", value)} руб", fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ResultRowGray(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text("${String.format(Locale.US, "%.2f", value)} руб", fontSize = 10.sp, color = Color.Gray)
    }
}

// ==========================================
// КАРТОЧКА ИСТОРИИ
// ==========================================

@Composable
fun HistoryCard(
    historyList: List<SalaryHistoryRecord>,
    isDarkTheme: Boolean,
    historyManager: HistoryManager,
    onHistoryChanged: () -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.DarkGray),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📚 История выплат", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                if (historyList.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { historyManager.shareCsv(context) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Text("📤", fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Text("🗑️", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (historyList.isEmpty()) {
                Text("Пока нет сохранённых расчётов.", color = Color.Gray, fontSize = 10.sp)
            } else {
                historyList.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDarkTheme) Color(0xFF252525) else Color(0xFFEEEEEE),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.monthName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Ав: ${String.format(Locale.US, "%.1f", record.advance)} р.", fontSize = 9.sp, color = Color.Gray)
                                Text("На руки: ${String.format(Locale.US, "%.1f", record.cleanToPay)} р.", fontSize = 9.sp, color = Color(0xFF00E676))
                                Text("Итого: ${String.format(Locale.US, "%.1f", record.totalClean)} р.", fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(
                            text = "✕",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                historyManager.deleteRecord(record.monthIndex)
                                onHistoryChanged()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все сохранённые расчёты будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyManager.deleteAll()
                        onHistoryChanged()
                        showClearDialog = false
                    }
                ) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}