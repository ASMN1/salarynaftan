package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.salarynaftan.data.MonthSalaryEntity
import com.example.salarynaftan.data.SalaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SalaryCalculatorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val settingsManager: SettingsManager,
    private val salaryRepository: SalaryRepository
) : ViewModel() {

    enum class SalaryField {
        ZA_OTSUTSTVUUSHEGO,
        KVARTALKA,
        GAZETA,
        POZHERTVOVANJA,
        SUBBOTNIK,
        MM_DETI,
        CHILDREN_COUNT,
        STRAVITA
    }

    companion object {
        /** Максимально допустимая норма часов в месяц для валидации ввода. */
        const val MAX_NORM_HOURS = 500

        /** Максимальное число детей для вычета. */
        const val MAX_CHILDREN = 20

        /** Максимальное число базовых величин на детей. */
        const val MAX_MM_DETI = 100
    }

    private val _uiState = MutableStateFlow(SalaryUiState())
    val uiState: StateFlow<SalaryUiState> = _uiState.asStateFlow()

    private val months = MonthlyNorms.list

    init {
        val savedIndex = savedStateHandle.get<Int>("selectedMonthIndex")
            ?: settingsManager.getSelectedMonthIndex()
        val savedYear = savedStateHandle.get<Int>("selectedYear")
            ?: java.time.LocalDate.now().year
        _uiState.update {
            it.copy(selectedMonthIndex = savedIndex, selectedYear = savedYear)
        }
        loadMonthData(savedIndex, savedYear)
    }

    fun selectMonth(index: Int) {
        if (index in months.indices) {
            savedStateHandle["selectedMonthIndex"] = index
            settingsManager.saveSelectedMonthIndex(index)
            _uiState.update { it.copy(selectedMonthIndex = index) }
            loadMonthData(index, _uiState.value.selectedYear)
        }
    }

    fun selectYear(year: Int) {
        savedStateHandle["selectedYear"] = year
        _uiState.update { it.copy(selectedYear = year) }
        loadMonthData(_uiState.value.selectedMonthIndex, year)
    }

    // Параметры месяц/год передаются явно, чтобы быстрые переключения
    // не «гонялись»: каждая корутина читает состояние, актуальное на момент
    // вызова, а не обновлённое после старта (исправление гонки данных).
    private fun loadMonthData(monthIndex: Int, year: Int) {
        viewModelScope.launch {
            val month = months.getOrNull(monthIndex) ?: return@launch
            val saved = salaryRepository.getMonthData(year, monthIndex)

            // Норма часов и праздничные — всегда берутся автоматически:
            // норма из справочника по году (MonthlyNorms), праздничные из
            // календаря (Holidays). Ручной ввод этих полей убран — значения
            // не меняются и всегда согласованы с графиком.
            val norm = MonthlyNorms.norm(year, monthIndex).toString()
            val holidayHours = SalaryCalculator.monthStats(
                year = year,
                monthIndex = monthIndex,
                brigade = settingsManager.getBrigade(),
                missedDays = emptySet(),
                vacationDays = emptySet()
            ).holidayHours
            val prazdn = if (holidayHours > 0) holidayHours.toString() else "0"
            val otsut = saved?.zaOtsutstvuushego ?: ""
            val kvart = saved?.kvartalka ?: ""
            val gaz = saved?.gazetaInput ?: "0"
            val poz = saved?.pozhertvovanjaInput ?: "0"
            val sub = saved?.subbotnikInput ?: "0"
            val mmdeti = saved?.mmDetiCountInput ?: "0"
            val children = saved?.childrenCountInput ?: "0"
            val stravita = saved?.stravitaInput ?: "0"

            _uiState.update {
                it.copy(
                    selectedMonthIndex = monthIndex,
                    selectedYear = year,
                    normHours = norm,
                    prazdnHours = prazdn,
                    zaOtsutstvuushego = otsut,
                    kvartalka = kvart,
                    gazetaInput = gaz,
                    pozhertvovanjaInput = poz,
                    subbotnikInput = sub,
                    mmDetiCountInput = mmdeti,
                    childrenCountInput = children,
                    stravitaInput = stravita,
                    errorMessage = null,
                    showResults = false,
                    calculationResult = null
                )
            }
        }
    }

    fun updateField(field: SalaryField, value: String) {
        _uiState.update { current ->
            when (field) {
                SalaryField.ZA_OTSUTSTVUUSHEGO -> current.copy(zaOtsutstvuushego = value)
                SalaryField.KVARTALKA -> current.copy(kvartalka = value)
                SalaryField.GAZETA -> current.copy(gazetaInput = value)
                SalaryField.POZHERTVOVANJA -> current.copy(pozhertvovanjaInput = value)
                SalaryField.SUBBOTNIK -> current.copy(subbotnikInput = value)
                SalaryField.MM_DETI -> current.copy(mmDetiCountInput = value)
                SalaryField.CHILDREN_COUNT -> current.copy(childrenCountInput = value)
                SalaryField.STRAVITA -> current.copy(stravitaInput = value)
            }
        }
    }

    fun performCalculation() {
        viewModelScope.launch {
            val state = uiState.value

            // ---- Валидация входных данных перед расчётом ----
            val errors = mutableListOf<String>()
            val norm = parseNonNegative(state.normHours)
            val prazdn = parseNonNegative(state.prazdnHours)
            val children = parseNonNegative(state.childrenCountInput)
            val mmDeti = parseNonNegative(state.mmDetiCountInput)
            if (norm <= 0) errors.add("Норма часов должна быть больше нуля")
            if (norm > MAX_NORM_HOURS) errors.add("Норма часов слишком велика (max $MAX_NORM_HOURS)")
            if (prazdn > norm && norm > 0) errors.add("Праздничных часов не может быть больше нормы")
            if (children > MAX_CHILDREN) errors.add("Некорректное число детей (max $MAX_CHILDREN)")
            if (mmDeti > MAX_MM_DETI) errors.add("Некорректное число базовых величин на детей (max $MAX_MM_DETI)")

            if (errors.isNotEmpty()) {
                _uiState.update {
                    it.copy(errorMessage = errors.joinToString("\n"), showResults = false, calculationResult = null)
                }
                return@launch
            }

            val result = calculateForState(state)
            if (result.error != null) {
                _uiState.update { it.copy(errorMessage = result.error, showResults = false) }
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        showResults = true,
                        calculationResult = result
                    )
                }
            }
            saveCurrentMonthData()
        }
    }

    // Единая точка входа в чистую логику расчёта (SalaryCalculator),
    // которая подтягивает данные по невыходам/отпуску текущего и прошлого месяца.
    private suspend fun calculateForState(state: SalaryUiState): CalculationResultWithError {
        val monthIndex = state.selectedMonthIndex
        val year = state.selectedYear

        val inputs = SalaryCalculator.CalcInputs(
            okladBase = settingsManager.getSalary(),
            koefStazh = settingsManager.getStazhKoef(),
            koefPrem = settingsManager.getPremiumCoef(),
            currentBrigade = settingsManager.getBrigade(),
            currentMissed = salaryRepository.getMissedDays(year, monthIndex),
            currentVacation = salaryRepository.getVacationDays(year, monthIndex),
            prevMonthData = salaryRepository.getMonthData(
                if (monthIndex == 0) year - 1 else year,
                (monthIndex - 1 + 12) % 12
            ),
            prevMissed = salaryRepository.getMissedDays(
                if (monthIndex == 0) year - 1 else year,
                (monthIndex - 1 + 12) % 12
            ),
            prevVacation = salaryRepository.getVacationDays(
                if (monthIndex == 0) year - 1 else year,
                (monthIndex - 1 + 12) % 12
            )
        )

        return SalaryCalculator.calculate(
            year = year,
            monthIndex = monthIndex,
            monthData = SalaryCalculator.monthInputFrom(state),
            inputs = inputs,
            pensionPercent = settingsManager.getPpsPercent()
        )
    }

    suspend fun saveToHistory(historyManager: HistoryManager) {
        val state = uiState.value
        val result = state.calculationResult ?: return
        historyManager.saveRecord(
            state.selectedMonthIndex,
            state.selectedYear,
            MonthlyNorms.list.getOrNull(state.selectedMonthIndex)?.name ?: "",
            result.totalClean,
            result.cleanToPay,
            result.avans
        )
    }

    fun saveCurrentMonth() {
        viewModelScope.launch { saveCurrentMonthData() }
    }

    private suspend fun saveCurrentMonthData() {
        val monthIndex = uiState.value.selectedMonthIndex
        val year = uiState.value.selectedYear
        val state = uiState.value
        // Загружаем существующую запись, чтобы не затереть missedDays/vacationDays
        val existing = salaryRepository.getMonthData(year, monthIndex)
        salaryRepository.saveMonthData(
            MonthSalaryEntity(
                year = year,
                monthIndex = monthIndex,
                normHours = state.normHours,
                prazdnHours = state.prazdnHours,
                zaOtsutstvuushego = state.zaOtsutstvuushego,
                kvartalka = state.kvartalka,
                gazetaInput = state.gazetaInput,
                pozhertvovanjaInput = state.pozhertvovanjaInput,
                subbotnikInput = state.subbotnikInput,
                mmDetiCountInput = state.mmDetiCountInput,
                childrenCountInput = state.childrenCountInput,
                stravitaInput = state.stravitaInput,
                missedDays = existing?.missedDays ?: "",
                vacationDays = existing?.vacationDays ?: ""
            )
        )
    }
}
