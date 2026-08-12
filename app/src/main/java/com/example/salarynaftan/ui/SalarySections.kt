package com.example.salarynaftan.ui

import com.example.salarynaftan.AppNotifier
import com.example.salarynaftan.MoneyFormatter
import com.example.salarynaftan.R
import com.example.salarynaftan.SalaryUiState
import com.example.salarynaftan.SettingsManager
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
    msgSaved: String
) {
    ExpandableSection(
        title = stringResource(R.string.salary_section_salary_settings),
        initiallyExpanded = true
    ) {
        // rememberSaveable — чтобы введённые, но не сохранённые значения
        // оклада/коэффициентов переживали поворот экрана (потеря ввода).
        var salaryText by rememberSaveable { mutableStateOf(MoneyFormatter.format(settings.getSalary())) }
        var premiumText by rememberSaveable { mutableStateOf(percentInput(settings.getPremiumCoef())) }
        var stazhText by rememberSaveable { mutableStateOf(percentInput(settings.getStazhKoef())) }
        var ppsText by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", settings.getPpsPercent())) }
        var saveError by remember { mutableStateOf<String?>(null) }

        // Строки валидации, доступные и внутри onClick-лямбды (не @Composable)
        val vErrSalary = stringResource(R.string.salary_err_salary_positive)
        val vErrPremium = stringResource(R.string.salary_err_premium_range)
        val vErrStazh = stringResource(R.string.salary_err_stazh_range)
        val vErrPps = stringResource(R.string.salary_err_pps_range)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = salaryText, onValueChange = { salaryText = it; saveError = null },
                label = stringResource(R.string.salary_field_salary), icon = "💰", modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = premiumText, onValueChange = { premiumText = it; saveError = null },
                label = stringResource(R.string.salary_field_premium), icon = "📊", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = stazhText, onValueChange = { stazhText = it; saveError = null },
                label = stringResource(R.string.salary_field_stazh), icon = "📈", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = ppsText, onValueChange = { ppsText = it; saveError = null },
                label = stringResource(R.string.salary_field_pps), icon = "🏦", modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(
            onClick = {
                val sal = salaryText.replace(',', '.').replace(" ", "").toDoubleOrNull()
                val premPct = premiumText.replace(',', '.').replace(" ", "").toDoubleOrNull()
                val stazhPct = stazhText.replace(',', '.').replace(" ", "").toDoubleOrNull()
                val ppsPct = ppsText.replace(',', '.').replace(" ", "").toDoubleOrNull()
                // Валидация с понятным сообщением вместо молчаливого игнора (п.6.3)
                saveError = when {
                    sal == null || !sal.isFinite() || sal <= 0 -> vErrSalary
                    premPct == null || !premPct.isFinite() || premPct < 0 || premPct > 200 -> vErrPremium
                    stazhPct == null || !stazhPct.isFinite() || stazhPct < 0 || stazhPct > 200 -> vErrStazh
                    ppsPct == null || !ppsPct.isFinite() || ppsPct < 0 || ppsPct > 100 -> vErrPps
                    else -> null
                }
                if (saveError == null && sal != null && premPct != null && stazhPct != null && ppsPct != null) {
                    settings.saveSalary(sal); salaryText = MoneyFormatter.format(sal)
                    settings.savePremiumCoef(premPct / 100.0); premiumText = percentInput(premPct / 100.0)
                    settings.saveStazhKoef(stazhPct / 100.0); stazhText = percentInput(stazhPct / 100.0)
                    settings.savePpsPercent(ppsPct); ppsText = String.format(Locale.US, "%.1f", ppsPct)
                    AppNotifier.show(msgSaved)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                stringResource(R.string.salary_save), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        saveError?.let { err ->
            Text(
                text = "⚠️  $err",
                color = DesignTokens.Danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InputFieldWithText(
                value = uiState.childrenCountInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.CHILDREN_COUNT, it) },
                label = stringResource(R.string.salary_field_children), icon = "👶", modifier = Modifier.width(120.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
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
    ExpandableSection(title = stringResource(R.string.salary_section_premiums), initiallyExpanded = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = uiState.zaOtsutstvuushego,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ZA_OTSUTSTVUUSHEGO, it) },
                label = stringResource(R.string.salary_field_za_otsutstvuushego), icon = "👤", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = uiState.kvartalka,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, it) },
                label = stringResource(R.string.salary_field_kvartalka), icon = "💰", modifier = Modifier.weight(1f)
            )
        }
        InputFieldWithText(
            value = uiState.mmDetiCountInput,
            onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.MM_DETI, it) },
            label = stringResource(R.string.salary_field_mm_deti), icon = "👪", modifier = Modifier.fillMaxWidth()
        )
    }
}

// ===== СЕКЦИЯ 3: УДЕРЖАНИЯ =====
@Composable
fun DeductionsSection(
    uiState: SalaryUiState,
    viewModel: SalaryCalculatorViewModel
) {
    ExpandableSection(title = stringResource(R.string.salary_section_deductions), initiallyExpanded = false, danger = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = uiState.gazetaInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.GAZETA, it) },
                label = stringResource(R.string.salary_field_gazeta), icon = "📰", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = uiState.pozhertvovanjaInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.POZHERTVOVANJA, it) },
                label = stringResource(R.string.salary_field_pozhertvovanja), icon = "❤️", modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputFieldWithText(
                value = uiState.subbotnikInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.SUBBOTNIK, it) },
                label = stringResource(R.string.salary_field_subbotnik), icon = "🧹", modifier = Modifier.weight(1f)
            )
            InputFieldWithText(
                value = uiState.stravitaInput,
                onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.STRAVITA, it) },
                label = stringResource(R.string.salary_field_stravita), icon = "🏥", modifier = Modifier.weight(1f)
            )
        }
    }
}