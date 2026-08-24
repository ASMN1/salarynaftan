package com.example.salarynaftan.ui

import com.example.salarynaftan.AppNotifier
import com.example.salarynaftan.MoneyFormatter
import com.example.salarynaftan.R
import com.example.salarynaftan.STAZH_COEF_OPTIONS
import com.example.salarynaftan.RANK_BASE_RATE_OPTIONS
import com.example.salarynaftan.SalaryUiState
import com.example.salarynaftan.SettingsManager
import com.example.salarynaftan.coefOptionLabel
import com.example.salarynaftan.harmClassOptions
import com.example.salarynaftan.percentInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Секции экрана расчёта зарплаты (п.3.1): вынесены из SalaryCalculatorScreen,
 * чтобы экран оставался тонким оркестратором, а каждая секция была отдельным
 * переиспользуемым composable.
 */

// ===== НАСТРОЙКИ ОКЛАДА И КОЭФФИЦИЕНТОВ =====
@Composable
fun SalarySettingsSection(
    settings: SettingsManager,
    msgSaved: String,
    viewModel: SalaryCalculatorViewModel,
    uiState: SalaryUiState
) {
    ExpandableSection(
        title = stringResource(R.string.salary_section_salary_settings),
        initiallyExpanded = true
    ) {
        // rememberSaveable — чтобы введённые, но не сохранённые значения переживали
        // поворот экрана. Дефолты читаем синхронно через remember (один раз); DataStore
        // уже прогрет через warmUp(), а load() использует Dispatchers.IO.
        var salaryText by rememberSaveable { mutableStateOf(MoneyFormatter.format(settings.getSalary())) }
        var premiumText by rememberSaveable { mutableStateOf(percentInput(settings.getPremiumCoef())) }
        var stazhCoef by rememberSaveable { mutableStateOf(settings.getStazhKoef()) }
        var harmClassCoef by rememberSaveable { mutableStateOf(settings.getHarmClassCoef()) }
        var baseRate by rememberSaveable { mutableStateOf(settings.getBaseRateRank()) }
        var profText by rememberSaveable { mutableStateOf(pctStr(settings.getProfCoef())) }
        var intensText by rememberSaveable { mutableStateOf(pctStr(settings.getIntensCoef())) }
        var ppsText by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", settings.getPpsPercent())) }
        val pctToCoef: (String) -> Double? = { it.replace(',', '.').replace(" ", "").toDoubleOrNull()?.let { v -> v / 100.0 } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = salaryText,
                onValueChange = { t ->
                    salaryText = t
                    val v = t.replace(',', '.').replace(" ", "").toDoubleOrNull()
                    if (v != null && v.isFinite() && v > 0) settings.saveSalary(v)
                },
                label = stringResource(R.string.salary_field_salary), icon = "💰", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = premiumText,
                onValueChange = { t ->
                    premiumText = t
                    val v = t.replace(',', '.').replace(" ", "").toDoubleOrNull()
                    if (v != null && v.isFinite()) settings.savePremiumCoef(v / 100.0)
                },
                label = stringResource(R.string.salary_field_premium), icon = "📊", modifier = Modifier.weight(1f)
            )
        }
        // Стаж и класс вредности — селекторы с авто-коэффициентом (из Зарплата6.xlsx).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorField(
                value = coefOptionLabel(STAZH_COEF_OPTIONS, stazhCoef),
                label = "Стаж",
                options = STAZH_COEF_OPTIONS.map { it.label },
                icon = "📈",
                modifier = Modifier.weight(1f),
                onOptionSelected = { label -> STAZH_COEF_OPTIONS.find { it.label == label }?.let { stazhCoef = it.coef; settings.saveStazhKoef(it.coef) } }
            )
            SelectorField(
                value = coefOptionLabel(harmClassOptions, harmClassCoef),
                label = "Класс вредности",
                options = harmClassOptions.map { it.label },
                icon = "☣️",
                modifier = Modifier.weight(1f),
                onOptionSelected = { label -> harmClassOptions.find { it.label == label }?.let { harmClassCoef = it.coef; settings.saveHarmClassCoef(it.coef) } }
            )
        }
        // «Разряд» и «Доплата (ППС)» — каждая на всю ширину, чтобы длинная
        // подпись ППС не переносилась в узком поле и ряdisо был ровным.
        SelectorField(
            value = coefOptionLabel(RANK_BASE_RATE_OPTIONS, baseRate),
            label = "Разряд (базовая ставка)",
            options = RANK_BASE_RATE_OPTIONS.map { it.label },
            icon = "🔠",
            modifier = Modifier.fillMaxWidth(),
            onOptionSelected = { label -> RANK_BASE_RATE_OPTIONS.find { it.label == label }?.let { baseRate = it.coef; settings.saveBaseRateRank(it.coef) } }
        )
        InputFieldWithText(
            value = ppsText,
            onValueChange = { t ->
                ppsText = t
                val v = t.replace(',', '.').replace(" ", "").toDoubleOrNull()
                if (v != null && v.isFinite()) settings.savePpsPercent(v)
            },
            label = stringResource(R.string.salary_field_pps), icon = "🏦", modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = profText,
                onValueChange = { t ->
                    profText = t
                    pctToCoef(t)?.let { if (it in 0.0..1.0) settings.saveProfCoef(it) }
                },
                label = stringResource(R.string.salary_field_prof), icon = "🎓", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = intensText,
                onValueChange = { t ->
                    intensText = t
                    pctToCoef(t)?.let { if (it in 0.0..1.0) settings.saveIntensCoef(it) }
                },
                label = stringResource(R.string.salary_field_intens), icon = "⚡", modifier = Modifier.weight(1f)
            )
        }
        // Блок «Дети» перенесён сюда из «Рабочего времени» по запросу пользователя.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InputFieldWithText(
                value = uiState.childrenCountInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.CHILDREN_COUNT, it) },
                label = stringResource(R.string.salary_field_children), icon = "👶", modifier = Modifier.weight(1f)
            )
        }

        // Значения оклада, премии, стажа, класса вредности, профмастерства,
        // интенсивности и ППС сохраняются сразу при вводе — кнопка «Сохранить»
        // больше не нужна: после ввода можно сразу нажимать «Рассчитать».
    }
}

// ===== СЕКЦИЯ 1: РАБОЧЕЕ ВРЕМЯ =====
@Composable
fun WorkTimeSection(
    uiState: SalaryUiState,
    settings: SettingsManager,
    viewModel: SalaryCalculatorViewModel,
    msgSaved: String
) {
    ExpandableSection(title = stringResource(R.string.salary_section_work_time), initiallyExpanded = true) {
        // Норма часов всегда берётся автоматически из справочника MonthlyNorms
        // (для обоих графиков: №1 — 35-часовая неделя, №2 — 40-часовая).
        // Праздничные часы рассчитываются в SalaryCalculator.calculate() через
        // stats.holidayHours — ручной ввод этих полей убран.
        // Блок «Дети» перенесён в «Оклад и коэф» — здесь остаётся только кнопка сохранения.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { viewModel.saveCurrentMonth(); AppNotifier.show(msgSaved) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.salary_save), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ===== СЕКЦИЯ 2: ПРЕМИИ И ВЫПЛАТЫ =====
@Composable
fun PremiumsSection(
    uiState: SalaryUiState,
    viewModel: SalaryCalculatorViewModel
) {
    // Локальное состояние ввода: синхронизация с ViewModel только по потере фокуса
    var zaOtsutstvuushego by remember { mutableStateOf(uiState.zaOtsutstvuushego) }
    var kvartalka by remember { mutableStateOf(uiState.kvartalka) }
    var mmDeti by remember { mutableStateOf(uiState.mmDetiCountInput) }
    var inyeVyplaty by remember { mutableStateOf(uiState.inyeVyplatyInput) }
    // Одноразовое обновление при изменении извне (смена месяца)
    LaunchedEffect(uiState.zaOtsutstvuushego, uiState.kvartalka, uiState.mmDetiCountInput, uiState.inyeVyplatyInput) {
        zaOtsutstvuushego = uiState.zaOtsutstvuushego
        kvartalka = uiState.kvartalka
        mmDeti = uiState.mmDetiCountInput
        inyeVyplaty = uiState.inyeVyplatyInput
    }
    ExpandableSection(title = stringResource(R.string.salary_section_premiums), initiallyExpanded = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = zaOtsutstvuushego,
                onValueChange = { zaOtsutstvuushego = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ZA_OTSUTSTVUUSHEGO, it) },
                label = stringResource(R.string.salary_field_za_otsutstvuushego), icon = "👤", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = kvartalka,
                onValueChange = { kvartalka = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, it) },
                label = stringResource(R.string.salary_field_kvartalka), icon = "💰", modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = mmDeti,
                onValueChange = { mmDeti = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.MM_DETI, it) },
                label = stringResource(R.string.salary_field_mm_deti), icon = "👪", modifier = Modifier.weight(1f)
            )
        }
        ExtraItemsInput(
            value = inyeVyplaty,
            onValueChange = { inyeVyplaty = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.INYE_VYPLATY, it) },
            label = stringResource(R.string.salary_field_inye_vyplaty), icon = "✨",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

// ===== СЕКЦИЯ 3: УДЕРЖАНИЯ =====
@Composable
fun DeductionsSection(
    uiState: SalaryUiState,
    viewModel: SalaryCalculatorViewModel
) {
    // Локальное состояние ввода: синхронизация с ViewModel только по потере фокуса
    var gazeta by remember { mutableStateOf(uiState.gazetaInput) }
    var pozhertvovanja by remember { mutableStateOf(uiState.pozhertvovanjaInput) }
    var subbotnik by remember { mutableStateOf(uiState.subbotnikInput) }
    var stravita by remember { mutableStateOf(uiState.stravitaInput) }
    var inyeUderzhanija by remember { mutableStateOf(uiState.inyeUderzhanijaInput) }
    LaunchedEffect(uiState.gazetaInput, uiState.pozhertvovanjaInput, uiState.subbotnikInput, uiState.stravitaInput, uiState.inyeUderzhanijaInput) {
        gazeta = uiState.gazetaInput
        pozhertvovanja = uiState.pozhertvovanjaInput
        subbotnik = uiState.subbotnikInput
        stravita = uiState.stravitaInput
        inyeUderzhanija = uiState.inyeUderzhanijaInput
    }
    ExpandableSection(title = stringResource(R.string.salary_section_deductions), initiallyExpanded = false, danger = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = gazeta,
                onValueChange = { gazeta = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.GAZETA, it) },
                label = stringResource(R.string.salary_field_gazeta), icon = "📰", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = pozhertvovanja,
                onValueChange = { pozhertvovanja = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.POZHERTVOVANJA, it) },
                label = stringResource(R.string.salary_field_pozhertvovanja), icon = "❤️", modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = subbotnik,
                onValueChange = { subbotnik = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.SUBBOTNIK, it) },
                label = stringResource(R.string.salary_field_subbotnik), icon = "🧹", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = stravita,
                onValueChange = { stravita = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.STRAVITA, it) },
                label = stringResource(R.string.salary_field_stravita), icon = "🏥", modifier = Modifier.weight(1f)
            )
        }
        ExtraItemsInput(
            value = inyeUderzhanija,
            onValueChange = { inyeUderzhanija = it; viewModel.updateField(SalaryCalculatorViewModel.SalaryField.INYE_UDERZHANIJA, it) },
            label = stringResource(R.string.salary_field_inye_uderzhanija), icon = "🚫",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

/** Отображает коэффициент (0..1) как десятичный процент (0.32 → «32», 0.005 → «0.5»). */
private fun pctStr(coef: Double): String =
    String.format(java.util.Locale.US, "%.4f", coef * 100)
        .trimEnd('0').trimEnd('.').ifEmpty { "0" }