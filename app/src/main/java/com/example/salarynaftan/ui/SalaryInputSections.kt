package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== ВЫБОР МЕСЯЦА =====

@Composable
fun MonthSelector(
    selectedMonthIndex: Int,
    onMonthSelected: (Int) -> Unit,
    selectedYear: Int = java.time.LocalDate.now().year,
    onYearSelected: (Int) -> Unit = {}
) {
    val months = MonthlyNorms.list
    var expanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    // 2026 оставляем доступным: для него норма берётся по умолчанию из
    // MonthlyNorms.list. Годы 2027+ — из таблицы точных норм (supportedYears).
    val years = MonthlyNorms.supportedYears().toList()
    val primary = MaterialTheme.colorScheme.primary

    PremiumSectionCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Период расчёта",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Строка: месяц | год
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MonthYearField(
                        value = months[selectedMonthIndex].name,
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        months.forEachIndexed { index, monthData ->
                            DropdownMenuItem(
                                text = { Text(monthData.name, fontSize = 14.sp, fontWeight = if (index == selectedMonthIndex) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onMonthSelected(index)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(0.62f)) {
                    MonthYearField(
                        value = selectedYear.toString(),
                        onClick = { yearExpanded = true }
                    )
                    DropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.5f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString(), fontSize = 14.sp, fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onYearSelected(year)
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthYearField(value: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ===== РАСКРЫВАЕМАЯ СЕКЦИЯ =====

@Composable
fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val dangerColor = DesignTokens.Danger
    val accentColor = if (danger) dangerColor else MaterialTheme.colorScheme.primary

    PremiumSectionCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Цветной индикатор слева
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 34.dp)
                        .background(accentColor.copy(alpha = if (expanded) 1f else 0.4f), RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (danger) "⚠️" else "📌",
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (danger) dangerColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = accentColor,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 0.dp)
                            .background(Color.Transparent)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ===== ПОЛЕ ВВОДА С ИКОНКОЙ =====

/**
 * Поле ввода с иконкой и опциональным колбэком при потере фокуса (onDone).
 * onDone вызывается при focus=false — позволяет синхронизировать значение
 * с ViewModel без рекомпозиции на каждый символ (производительность).
 */
@Composable
fun InputFieldWithText(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: String,
    modifier: Modifier = Modifier,
    onFocusLost: ((String) -> Unit)? = null
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Text(icon, fontSize = 16.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .onFocusChanged { if (!it.isFocused && onFocusLost != null) onFocusLost(value) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    )
}

// ===== РЕДАКТИРУЕМЫЙ СПИСОК «ИНЫХ» ВЫПЛАТ/УДЕРЖАНИЙ =====

/** Внутренняя редактируемая строка одной позиции. */
private data class ExtraDraft(val name: String = "", val amount: String = "")

/**
 * Список «иных» выплат/удержаний: каждая строка имеет название и сумму,
 * строки добавляются кнопкой «+ Добавить» и удаляются кнопкой «✕».
 * Состояние хранится построчно, а во внешнюю строку собирается как
 * `Название:Сумма;...` через [buildExtraRaw] (для сохранения и расчёта).
 */
@Composable
fun ExtraItemsInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    val drafts = remember { mutableStateListOf<ExtraDraft>() }
    // Последнее значение, которое мы сами сгенерировали. Нужен, чтобы отличать
    // собственные изменения от внешних (смена месяца/загрузка данных).
    var lastEmitted by remember { mutableStateOf<String?>(null) }

    // Синхронизация извне: если пришло не наше значение — перестроить список.
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            lastEmitted = value
            val parsed = parseExtraItems(value)
            drafts.clear()
            drafts.addAll(
                parsed.map { ExtraDraft(it.name, if (it.amount > 0) MoneyFormatter.format(it.amount) else "") }
            )
            if (drafts.isEmpty()) drafts.add(ExtraDraft())
        }
    }

    fun emit() {
        val raw = buildExtraRaw(
            drafts.map { ExtraItem(it.name, parseNonNegative(it.amount)) }
        )
        lastEmitted = raw
        onValueChange(raw)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$icon  $label",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Существующие строки
        drafts.forEachIndexed { index, draft ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { t -> drafts[index] = draft.copy(name = t); emit() },
                    label = { Text("Название", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.weight(1.4f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = draft.amount,
                    onValueChange = { t ->
                        drafts[index] = draft.copy(amount = t.filter { it.isDigit() || it == ',' || it == '.' })
                        emit()
                    },
                    label = { Text("Сумма", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(contentAlignment = Alignment.Center) {
                    TextButton(onClick = { drafts.removeAt(index); emit() }) {
                        Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Итог
        val total = drafts.sumOf { parseNonNegative(it.amount) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Итого", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(MoneyFormatter.formatRub(total), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        // Кнопка добавить
        OutlinedButton(
            onClick = { drafts.add(ExtraDraft()); emit() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("＋ Добавить " + label.lowercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ===== ВЫПАДАЮЩИЙ СЕЛЕКТОР (стаж, класс вредности) =====

// ===== ВЫПАДАЮЩИЙ СЕЛЕКТОР (стаж, класс вредности) =====

/**
 * Одно-выборное поле-кнопка с выпадающим списком. Используется в «Окладе и
 * коэффициентах» для выбора стажа и класса вредности (авто-коэффициент).
 */
@Composable
fun SelectorField(
    value: String,
    label: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    icon: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(18.dp)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option, fontSize = 14.sp,
                                fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
