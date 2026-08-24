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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class SalaryCalculatorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val settingsManager: SettingsManager,
    private val salaryRepository: SalaryRepository,
    private val appScope: CoroutineScope
) : ViewModel() {

    enum class SalaryField {
        ZA_OTSUTSTVUUSHEGO,
        KVARTALKA,
        GAZETA,
        POZHERTVOVANJA,
        SUBBOTNIK,
        MM_DETI,
        CHILDREN_COUNT,
        STRAVITA,
        INYE_VYPLATY,
        INYE_UDERZHANIJA
    }

    companion object {
        /** Максимально допустимая норма часов в месяц для валидации ввода. */
        const val MAX_NORM_HOURS = 500

        /** Минимально возможная норма часов в месяц (защита от деления на крошечную норму). */
        const val MIN_NORM_HOURS = 40

        /** Максимальное число детей для вычета. */
        const val MAX_CHILDREN = 20

        /** Максимальное число базовых величин на детей. */
        const val MAX_MM_DETI = 100
    }

    private val _uiState = MutableStateFlow(SalaryUiState())
    val uiState: StateFlow<SalaryUiState> = _uiState.asStateFlow()

    // Отменяет предыдущее сохранение при быстром переключении месяца/года,
    // чтобы конкурентные записи в Room не перезаписывали данные друг друга (п.4.5).
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private var inputSaveJob: Job? = null

    private val months = MonthlyNorms.list

    init {
        val savedIndex = savedStateHandle.get<Int>("selectedMonthIndex")
            ?: settingsManager.getSelectedMonthIndex()
        val savedYear = savedStateHandle.get<Int>("selectedYear")
            ?: java.time.LocalDate.now().year
        // Сохраняем выбранный год как есть — без принудительного приведения
        // к диапазону таблицы норм (supportedYears). Иначе год за пределами
        // таблицы (например 2026) незаметно меняется, и расчёт премии идёт
        // по чужому году (премия считается по прошлому месяцу этого года).
        _uiState.update {
            it.copy(selectedMonthIndex = savedIndex, selectedYear = savedYear)
        }
        loadMonthData(savedIndex, savedYear)
    }

    fun selectMonth(index: Int) {
        if (index in months.indices && index != _uiState.value.selectedMonthIndex) {
            // Захватываем состояние ДО обновления: saveCurrentMonthData читает
            // uiState внутри корутины, и без захвата сохранились бы данные
            // НОВОГО месяца (гонка при быстром переключении).
            val stateToSave = _uiState.value
            saveJob?.cancel()
            saveJob = viewModelScope.launch {
                try { saveCurrentMonthData(stateToSave) } catch (e: Exception) { AppNotifier.showError("Не удалось сохранить месяц") }
            }
            savedStateHandle["selectedMonthIndex"] = index
            settingsManager.saveSelectedMonthIndex(index)
            _uiState.update { it.copy(selectedMonthIndex = index) }
            loadMonthData(index, _uiState.value.selectedYear)
        }
    }

    fun selectYear(year: Int) {
        if (year != _uiState.value.selectedYear) {
            val stateToSave = _uiState.value
            saveJob?.cancel()
            saveJob = viewModelScope.launch {
                try { saveCurrentMonthData(stateToSave) } catch (e: Exception) { AppNotifier.showError("Не удалось сохранить месяц") }
            }
            savedStateHandle["selectedYear"] = year
            _uiState.update { it.copy(selectedYear = year) }
            loadMonthData(_uiState.value.selectedMonthIndex, year)
        }
    }

    // Параметры месяц/год передаются явно, чтобы быстрые переключения
    // не «гонялись»: каждая корутина читает состояние, актуальное на момент
    // вызова, а не обновлённое после старта (исправление гонки данных).
    private fun loadMonthData(monthIndex: Int, year: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val month = months.getOrNull(monthIndex) ?: return@launch
                val saved = salaryRepository.getMonthData(year, monthIndex)

                // Норма часов всегда авто из справочника, праздничные — в SalaryCalculator.
                val scheduleType = settingsManager.getScheduleType()
                val norm = MonthlyNorms.norm(year, monthIndex, scheduleType).toString()
                val otsut = saved?.zaOtsutstvuushego ?: ""
                val kvart = saved?.kvartalka ?: ""
                val gaz = saved?.gazetaInput ?: "0"
                val poz = saved?.pozhertvovanjaInput ?: "0"
                val sub = saved?.subbotnikInput ?: "0"
                val mmdeti = saved?.mmDetiCountInput ?: "0"
                val children = saved?.childrenCountInput ?: "0"
                val stravita = saved?.stravitaInput ?: "0"
                val inyeV = saved?.inyeVyplatyInput ?: "0"
                val inyeU = saved?.inyeUderzhanijaInput ?: "0"

                if (_uiState.value.selectedMonthIndex != monthIndex || _uiState.value.selectedYear != year) return@launch
                _uiState.update {
                    it.copy(
                        selectedMonthIndex = monthIndex,
                        selectedYear = year,
                        normHours = norm,
                        zaOtsutstvuushego = otsut,
                        kvartalka = kvart,
                        gazetaInput = gaz,
                        pozhertvovanjaInput = poz,
                        subbotnikInput = sub,
                        mmDetiCountInput = mmdeti,
                        childrenCountInput = children,
                        stravitaInput = stravita,
                        inyeVyplatyInput = inyeV,
                        inyeUderzhanijaInput = inyeU,
                        errorMessage = null,
                        showResults = false,
                        calculationResult = null
                    )
                }
            } catch (e: Exception) {
                AppNotifier.showError("Не удалось загрузить данные месяца: ${e.message}")
            }
        }
    }

    fun updateField(field: SalaryField, value: String) {
        // Для «иных выплат/удержаний» разрешаем и текст названий (со знаком «:» и
        // разделителем «;»), для остальных числовых — только цифры, запятую, точку.
        val sanitized = if (field == SalaryField.INYE_VYPLATY || field == SalaryField.INYE_UDERZHANIJA) {
            value
        } else {
            value.filter { it.isDigit() || it == ',' || it == '.' }
        }
        val digitsOnly = sanitized.filter { it.isDigit() }
        _uiState.update { current ->
            when (field) {
                SalaryField.ZA_OTSUTSTVUUSHEGO -> current.copy(zaOtsutstvuushego = sanitized)
                SalaryField.KVARTALKA -> current.copy(kvartalka = sanitized)
                SalaryField.GAZETA -> current.copy(gazetaInput = sanitized)
                SalaryField.POZHERTVOVANJA -> current.copy(pozhertvovanjaInput = sanitized)
                SalaryField.SUBBOTNIK -> current.copy(subbotnikInput = sanitized)
                SalaryField.MM_DETI ->
                    current.copy(mmDetiCountInput = if (digitsOnly.toIntOrNull()?.let { it <= MAX_MM_DETI } == true) sanitized else current.mmDetiCountInput)
                SalaryField.CHILDREN_COUNT ->
                    current.copy(childrenCountInput = if (digitsOnly.toIntOrNull()?.let { it <= MAX_CHILDREN } == true) sanitized else current.childrenCountInput)
                SalaryField.STRAVITA -> current.copy(stravitaInput = sanitized)
                SalaryField.INYE_VYPLATY -> current.copy(inyeVyplatyInput = sanitized)
                SalaryField.INYE_UDERZHANIJA -> current.copy(inyeUderzhanijaInput = sanitized)
            }
        }
        // Persist edits while the process is alive; onCleared is not guaranteed
        // to run during process death. Debouncing avoids a DB write per key.
        // Фоновое автосохранение — не критично для ввода: числа уже хранятся в
        // uiState и используются при расчёте. Разовый сбой записи не должен
        // показывать уведомление и отвлекать от ввода.
        inputSaveJob?.cancel()
        inputSaveJob = viewModelScope.launch {
            delay(300)
            runCatching { saveCurrentMonthData() }
        }
    }

    fun performCalculation() {
        viewModelScope.launch {
            try {
                val state = uiState.value

                // ---- Валидация входных данных перед расчётом ----
                val errors = mutableListOf<String>()
                val norm = parseNonNegative(state.normHours)
                val children = parseNonNegative(state.childrenCountInput)
                val mmDeti = parseNonNegative(state.mmDetiCountInput)
                if (norm <= 0) errors.add("Норма часов должна быть больше нуля")
                if (norm > MAX_NORM_HOURS) errors.add("Норма часов слишком велика (max $MAX_NORM_HOURS)")
                if (norm < MIN_NORM_HOURS) errors.add("Норма часов слишком мала (мин $MIN_NORM_HOURS)")
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
            } catch (e: Exception) {
                AppNotifier.showError("Не удалось выполнить расчёт: ${e.message}")
            }
        }
    }

    // Единая точка входа в чистую логику расчёта (SalaryCalculator),
    // которая подтягивает данные по невыходам/отпуску текущего и прошлого месяца.
    private suspend fun calculateForState(state: SalaryUiState): CalculationResultWithError {
        val monthIndex = state.selectedMonthIndex
        val year = state.selectedYear
        // Год и индекс предыдущего месяца (п.3.3): единый helper вместо
        // троекратного дублирования «if (monthIndex == 0) year - 1 else year».
        val (prevYear, prevMonth) = prevOf(year, monthIndex)

        val inputs = SalaryCalculator.CalcInputs(
            okladBase = settingsManager.getSalary(),
            koefStazh = settingsManager.getStazhKoef(),
            koefPrem = settingsManager.getPremiumCoef(),
            currentBrigade = settingsManager.getBrigade(),
            currentMissed = salaryRepository.getMissedDays(year, monthIndex),
            currentVacation = salaryRepository.getVacationDays(year, monthIndex),
            prevMonthData = salaryRepository.getMonthData(prevYear, prevMonth),
            prevMissed = salaryRepository.getMissedDays(prevYear, prevMonth),
            prevVacation = salaryRepository.getVacationDays(prevYear, prevMonth),
            scheduleType = settingsManager.getScheduleType(),
            harmClassCoef = settingsManager.getHarmClassCoef(),
            profCoef = settingsManager.getProfCoef(),
            intensCoef = settingsManager.getIntensCoef(),
            baseRate = settingsManager.getBaseRateRank()
        )

        return SalaryCalculator.calculate(
            year = year,
            monthIndex = monthIndex,
            monthData = monthInputFrom(state),
            inputs = inputs,
            pensionPercent = settingsManager.getPpsPercent()
        )
    }

    /** Возвращает (год, индекс) предыдущего месяца относительно [year]/[monthIndex]. */
    private fun prevOf(year: Int, monthIndex: Int): Pair<Int, Int> =
        if (monthIndex == 0) year - 1 to 11 else year to monthIndex - 1

    suspend fun saveToHistory(historyManager: HistoryManager) {
        val state = uiState.value
        val result = state.calculationResult ?: return
        // Не сохраняем расчёт с ошибкой в историю (п.6.7 аудита).
        if (result.error != null) return
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
        viewModelScope.launch {
            try { saveCurrentMonthData(); AppNotifier.show("Сохранено") }
            catch (e: Exception) { AppNotifier.showError("Не удалось сохранить: ${e.message}") }
        }
    }

    // Автосохранение при уничтожении ViewModel (сворачивание/убийство приложения):
    // несохранённый ввод не теряется (п.6.4).
    // ВАЖНО: viewModelScope уже отменён к моменту onCleared, поэтому используем
    // отдельный scope, иначе корутина не выполнится.
    override fun onCleared() {
        saveJob?.cancel()
        loadJob?.cancel()
        inputSaveJob?.cancel()
        val stateToSave = uiState.value
        // Application-owned scope переживает ViewModel, но явно принадлежит
        // приложению и не скрывает неуправляемый GlobalScope.
        appScope.launch {
            try { saveCurrentMonthData(stateToSave) } catch (_: Exception) { }
        }
        super.onCleared()
    }

    private suspend fun saveCurrentMonthData() {
        saveCurrentMonthData(uiState.value)
    }

    private suspend fun saveCurrentMonthData(state: SalaryUiState) {
        val monthIndex = state.selectedMonthIndex
        val year = state.selectedYear
        // Сохраняем через транзакцию: missedDays/vacationDays не затираются
        // гонкой (чтение existing и запись выполняются атомарно, см. СалентнаяRep).
        salaryRepository.saveMonthPreservingMissed(
            MonthSalaryEntity(
                year = year,
                monthIndex = monthIndex,
                normHours = state.normHours,
                zaOtsutstvuushego = state.zaOtsutstvuushego,
                kvartalka = state.kvartalka,
                gazetaInput = state.gazetaInput,
                pozhertvovanjaInput = state.pozhertvovanjaInput,
                subbotnikInput = state.subbotnikInput,
                mmDetiCountInput = state.mmDetiCountInput,
                childrenCountInput = state.childrenCountInput,
                stravitaInput = state.stravitaInput,
                inyeVyplatyInput = state.inyeVyplatyInput,
                inyeUderzhanijaInput = state.inyeUderzhanijaInput,
                missedDays = "",
                vacationDays = ""
            )
        )
    }
}
