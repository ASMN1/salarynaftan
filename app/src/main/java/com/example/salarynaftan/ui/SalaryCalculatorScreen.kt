package com.example.salarynaftan.ui
import com.example.salarynaftan.*
import com.example.salarynaftan.R

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import org.koin.compose.koinInject

// ==========================================
// ЭКРАН: РАСЧЁТ ЗАРПЛАТЫ
// ==========================================

@Composable
fun SalaryCalculatorScreen(
    isDarkTheme: Boolean,
    viewModel: SalaryCalculatorViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val historyManager = koinInject<HistoryManager>()
    val settings = koinInject<SettingsManager>()
    val historyList by historyManager.records.collectAsState()
    val availableYears by historyManager.availableYears.collectAsState()
    val selectedFilterYear by historyManager.selectedFilterYear.collectAsState()
    val primary = MaterialTheme.colorScheme.primary

    // История грузится при показе экрана и при возврате на вкладку (ON_RESUME),
    // чтобы подхватывать изменения, сделанные из другого места (п.4.6).
    // Одна точка загрузки: старт = тик 1, далее каждый ON_RESUME инкрементирует.
    var resumeTick by remember { mutableIntStateOf(1) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        historyManager.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        PremiumHeader(
            title = stringResource(R.string.salary_title),
            subtitle = stringResource(R.string.salary_subtitle)
        )

        // ===== ВЫБОР МЕСЯЦА И ГОДА =====
        MonthSelector(
            selectedMonthIndex = uiState.selectedMonthIndex,
            selectedYear = uiState.selectedYear,
            onMonthSelected = { viewModel.selectMonth(it) },
            onYearSelected = { viewModel.selectYear(it) }
        )

        // ===== НАСТРОЙКИ ОКЛАДА И КОЭФФИЦИЕНТОВ =====
        ExpandableSection(
            title = "Оклад и коэффициенты",
            initiallyExpanded = true
        ) {
            // rememberSaveable — чтобы введённые, но не сохранённые значения
            // оклада/коэффициентов переживали поворот экрана (потеря ввода).
            var salaryText by rememberSaveable { mutableStateOf(MoneyFormatter.format(settings.getSalary())) }
            var premiumText by rememberSaveable { mutableStateOf(percentInput(settings.getPremiumCoef())) }
            var stazhText by rememberSaveable { mutableStateOf(percentInput(settings.getStazhKoef())) }
            var ppsText by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", settings.getPpsPercent())) }
            var saveError by remember { mutableStateOf<String?>(null) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = salaryText, onValueChange = { salaryText = it; saveError = null },
                    label = "Оклад", icon = "💰", modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = premiumText, onValueChange = { premiumText = it; saveError = null },
                    label = "Премия, %", icon = "📊", modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = stazhText, onValueChange = { stazhText = it; saveError = null },
                    label = "Стаж, %", icon = "📈", modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = ppsText, onValueChange = { ppsText = it; saveError = null },
                    label = "ППС, %", icon = "🏦", modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = {
                    val sal = salaryText.replace(',', '.').replace(' ', '.').toDoubleOrNull()
                    val premPct = premiumText.replace(',', '.').replace(' ', '.').toDoubleOrNull()
                    val stazhPct = stazhText.replace(',', '.').replace(' ', '.').toDoubleOrNull()
                    val ppsPct = ppsText.replace(',', '.').replace(' ', '.').toDoubleOrNull()
                    // Валидация с понятным сообщением вместо молчаливого игнора (п.6.3)
                    saveError = when {
                        sal == null || sal <= 0 -> "Оклад должен быть положительным числом"
                        premPct == null || premPct < 0 || premPct > 200 -> "Коэф. премии — от 0 до 200%"
                        stazhPct == null || stazhPct < 0 || stazhPct > 200 -> "Коэф. стажа — от 0 до 200%"
                        ppsPct == null || ppsPct < 0 || ppsPct > 100 -> "ППС — от 0 до 100%"
                        else -> null
                    }
                    if (saveError == null && sal != null && premPct != null && stazhPct != null && ppsPct != null) {
                        settings.saveSalary(sal); salaryText = MoneyFormatter.format(sal)
                        settings.savePremiumCoef(premPct / 100.0); premiumText = percentInput(premPct / 100.0)
                        settings.saveStazhKoef(stazhPct / 100.0); stazhText = percentInput(stazhPct / 100.0)
                        settings.savePpsPercent(ppsPct); ppsText = String.format(Locale.US, "%.1f", ppsPct)
                        AppNotifier.show("Сохранено")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("💾 Сохранить", fontSize = 13.sp, color = primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 2.dp))
            }
            saveError?.let { err ->
                Text(
                    text = "⚠️  $err",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ===== СЕКЦИЯ 1: РАБОЧЕЕ ВРЕМЯ =====
        ExpandableSection(title = "Рабочее время", initiallyExpanded = true) {
            // Норма часов и праздничные часы рассчитываются автоматически
            // из справочника (MonthlyNorms) и календаря (Holidays) — вручную
            // не вводятся. Показываем их только для справки.
            val monthIndex = uiState.selectedMonthIndex
            val year = uiState.selectedYear
            val autoNorm = MonthlyNorms.norm(year, monthIndex).toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(
                    value = "$autoNorm", onValueChange = {},
                    label = "Норма (авто)", icon = "🕐", modifier = Modifier.weight(1f)
                )
                InputFieldWithText(
                    value = uiState.prazdnHours, onValueChange = {},
                    label = "Праздн. (авто)", icon = "🎉", modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                InputFieldWithText(value = uiState.childrenCountInput, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.CHILDREN_COUNT, it) }, label = "Детей", icon = "👶", modifier = Modifier.width(120.dp))
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.saveCurrentMonth(); AppNotifier.show("Сохранено") }, shape = RoundedCornerShape(14.dp)) {
                    Text("💾 Сохранить", fontSize = 13.sp, color = primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ===== СЕКЦИЯ 2: ПРЕМИИ И ВЫПЛАТЫ =====
        ExpandableSection(title = "Премии и доп. выплаты", initiallyExpanded = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(value = uiState.zaOtsutstvuushego, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.ZA_OTSUTSTVUUSHEGO, it) }, label = "За отсутств. (руб)", icon = "👤", modifier = Modifier.weight(1f))
                InputFieldWithText(value = uiState.kvartalka, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, it) }, label = "Кварталка (руб)", icon = "💰", modifier = Modifier.weight(1f))
            }
            InputFieldWithText(value = uiState.mmDetiCountInput, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.MM_DETI, it) }, label = "МП на детей до 3л (баз.вел.)", icon = "👪", modifier = Modifier.fillMaxWidth())
        }

        // ===== СЕКЦИЯ 3: УДЕРЖАНИЯ =====
        ExpandableSection(title = "Удержания и невыходы", initiallyExpanded = false, danger = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(value = uiState.gazetaInput, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.GAZETA, it) }, label = "Газета (руб)", icon = "📰", modifier = Modifier.weight(1f))
                InputFieldWithText(value = uiState.pozhertvovanjaInput, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.POZHERTVOVANJA, it) }, label = "Пожертв. (руб)", icon = "❤️", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputFieldWithText(value = uiState.subbotnikInput, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.SUBBOTNIK, it) }, label = "Субботник (руб)", icon = "🧹", modifier = Modifier.weight(1f))
                InputFieldWithText(value = uiState.stravitaInput, onValueChange = { viewModel.updateField(SalaryCalculatorViewModel.SalaryField.STRAVITA, it) }, label = "Стравита (руб)", icon = "🏥", modifier = Modifier.weight(1f))
            }
        }

        // ===== ОШИБКА =====
        if (uiState.errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Color(0xFFFF5252).copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "⚠️  ${uiState.errorMessage!!}",
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // ===== КНОПКА РАССЧИТАТЬ =====
        PremiumButton(
            text = "Рассчитать",
            icon = "🧮",
            onClick = { viewModel.performCalculation() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            containerColor = primary,
            contentColor = Color.Black
        )
        Spacer(modifier = Modifier.height(2.dp))

        // ===== РЕЗУЛЬТАТЫ =====
        AnimatedVisibility(visible = uiState.showResults && uiState.calculationResult != null, enter = fadeIn(tween(400)), exit = fadeOut(tween(200))) {
            val result = uiState.calculationResult ?: return@AnimatedVisibility
            ResultCard(
                state = uiState,
                result = result,
                months = MonthlyNorms.list,
                historyManager = historyManager,
                stazhPercent = (settings.getStazhKoef() * 100).toInt(),
                premiumPercent = (settings.getPremiumCoef() * 100).toInt(),
                pensionPercent = settings.getPpsPercent().toInt()
            )
        }

        // ===== ИСТОРИЯ =====
        HistoryCard(
            historyList = historyList,
            isDarkTheme = isDarkTheme,
            historyManager = historyManager,
            availableYears = availableYears,
            selectedFilterYear = selectedFilterYear
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}
