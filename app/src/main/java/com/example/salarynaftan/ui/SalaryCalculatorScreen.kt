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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

// ==========================================
// ЭКРАН: РАСЧЁТ ЗАРПЛАТЫ
// Тонкий оркестратор: секции вынесены в SalarySections.kt (п.3.1),
// чтобы каждый блок был отдельным переиспользуемым composable.
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
    // Общие строки-подписи, используемые в onClick-лямбдах (не @Composable)
    val msgSaved = stringResource(R.string.salary_saved)

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
        SalarySettingsSection(settings = settings, msgSaved = msgSaved)

        // ===== СЕКЦИЯ 1: РАБОЧЕЕ ВРЕМЯ =====
        WorkTimeSection(
            uiState = uiState,
            settings = settings,
            viewModel = viewModel,
            msgSaved = msgSaved
        )

        // ===== СЕКЦИЯ 2: ПРЕМИИ И ВЫПЛАТЫ =====
        PremiumsSection(uiState = uiState, viewModel = viewModel)

        // ===== СЕКЦИЯ 3: УДЕРЖАНИЯ =====
        DeductionsSection(uiState = uiState, viewModel = viewModel)

        // ===== ОШИБКА =====
        if (uiState.errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = DesignTokens.Danger.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "⚠️  ${uiState.errorMessage!!}",
                    color = DesignTokens.Danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // ===== КНОПКА РАССЧИТАТЬ =====
        PremiumButton(
            text = stringResource(R.string.salary_calculate),
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
                pensionPercent = settings.getPpsPercent().toInt(),
                onPrevMonth = {
                    val idx = (uiState.selectedMonthIndex - 1 + 12) % 12
                    viewModel.selectMonth(idx)
                },
                onNextMonth = {
                    val idx = (uiState.selectedMonthIndex + 1) % 12
                    viewModel.selectMonth(idx)
                }
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