package com.example.salarynaftan

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SalaryCalculatorViewModel(
    private val savedStateHandle: SavedStateHandle,
    appContext: Application,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val context: Context = appContext.applicationContext

    // ===== ВЛОЖЕННЫЙ ENUM ДЛЯ ПОЛЕЙ =====
    enum class SalaryField {
        NORM_HOURS,
        FACT_HOURS,
        NIGHT_SHIFTS,
        S4_SHIFTS,
        ADVANCE_SHIFTS,
        PRAZDN_HOURS,
        ZA_OTSUTSTVUUSHEGO,
        KVARTALKA,
        GAZETA,
        POZHERTVOVANJA,
        SUBBOTNIK,
        ZA_SVOY_SCHET,
        MM_DETI,
        CHILDREN_COUNT
    }

    private val _uiState = MutableStateFlow(SalaryUiState())
    val uiState: StateFlow<SalaryUiState> = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences(PreferenceKeys.SALARY_MONTHS_PREFS, Context.MODE_PRIVATE)
    private val months = MonthlyNorms.list

    init {
        val savedIndex = savedStateHandle.get<Int>("selectedMonthIndex") ?: 5
        _uiState.update { it.copy(selectedMonthIndex = savedIndex) }
        loadMonthData()
    }

    fun selectMonth(index: Int) {
        if (index in months.indices) {
            savedStateHandle["selectedMonthIndex"] = index
            _uiState.update { it.copy(selectedMonthIndex = index) }
            loadMonthData()
        }
    }

    private fun loadMonthData() {
        val monthIndex = uiState.value.selectedMonthIndex
        val month = months[monthIndex]

        val norm = prefs.getString("${PreferenceKeys.NORM_PREFIX}$monthIndex", month.norm.toString()) ?: month.norm.toString()
        val fact = prefs.getString("${PreferenceKeys.FACT_PREFIX}$monthIndex", month.fact.toString()) ?: month.fact.toString()
        val night = prefs.getString("${PreferenceKeys.NIGHT_PREFIX}$monthIndex", month.defaultNightShifts.toString()) ?: month.defaultNightShifts.toString()
        val s4 = prefs.getString("${PreferenceKeys.S4_PREFIX}$monthIndex", month.defaultS4Shifts.toString()) ?: month.defaultS4Shifts.toString()
        val advance = prefs.getString("${PreferenceKeys.ADV_PREFIX}$monthIndex", month.defaultAdvanceShifts.toString()) ?: month.defaultAdvanceShifts.toString()
        val prazdn = prefs.getString("${PreferenceKeys.PRAZDN_PREFIX}$monthIndex", "0") ?: "0"
        val otsut = prefs.getString("${PreferenceKeys.OTSUT_PREFIX}$monthIndex", "") ?: ""
        val kvart = prefs.getString("${PreferenceKeys.KVART_PREFIX}$monthIndex", "") ?: ""
        val gaz = prefs.getString("${PreferenceKeys.GAZ_PREFIX}$monthIndex", "0") ?: "0"
        val poz = prefs.getString("${PreferenceKeys.POZ_PREFIX}$monthIndex", "0") ?: "0"
        val sub = prefs.getString("${PreferenceKeys.SUB_PREFIX}$monthIndex", "0") ?: "0"
        val svoy = prefs.getString("${PreferenceKeys.SVOY_PREFIX}$monthIndex", "0") ?: "0"
        val mmdeti = prefs.getString("${PreferenceKeys.MMDETI_PREFIX}$monthIndex", "0") ?: "0"
        val children = prefs.getString("${PreferenceKeys.CHILDREN_PREFIX}$monthIndex", "2") ?: "2"

        _uiState.update {
            it.copy(
                normHours = norm,
                factHours = fact,
                nightShifts = night,
                s4Shifts = s4,
                advanceShifts = advance,
                prazdnHours = prazdn,
                zaOtsutstvuushego = otsut,
                kvartalka = kvart,
                gazetaInput = gaz,
                pozhertvovanjaInput = poz,
                subbotnikInput = sub,
                zaSvoySchetInput = svoy,
                mmDetiCountInput = mmdeti,
                childrenCountInput = children,
                errorMessage = null,
                showResults = false,
                calculationResult = null,
                effectiveFactText = ""
            )
        }
    }

    fun updateField(field: SalaryField, value: String) {
        _uiState.update { current ->
            when (field) {
                SalaryField.NORM_HOURS -> current.copy(normHours = value)
                SalaryField.FACT_HOURS -> current.copy(factHours = value)
                SalaryField.NIGHT_SHIFTS -> current.copy(nightShifts = value)
                SalaryField.S4_SHIFTS -> current.copy(s4Shifts = value)
                SalaryField.ADVANCE_SHIFTS -> current.copy(advanceShifts = value)
                SalaryField.PRAZDN_HOURS -> current.copy(prazdnHours = value)
                SalaryField.ZA_OTSUTSTVUUSHEGO -> current.copy(zaOtsutstvuushego = value)
                SalaryField.KVARTALKA -> current.copy(kvartalka = value)
                SalaryField.GAZETA -> current.copy(gazetaInput = value)
                SalaryField.POZHERTVOVANJA -> current.copy(pozhertvovanjaInput = value)
                SalaryField.SUBBOTNIK -> current.copy(subbotnikInput = value)
                SalaryField.ZA_SVOY_SCHET -> current.copy(zaSvoySchetInput = value)
                SalaryField.MM_DETI -> current.copy(mmDetiCountInput = value)
                SalaryField.CHILDREN_COUNT -> current.copy(childrenCountInput = value)
            }
        }
    }

    fun autoFillFromSchedule() {
        val monthIndex = uiState.value.selectedMonthIndex
        val currentBrigade = settingsManager.getBrigade()
        val (calcFact, calcNight, calcDay) = autoFillFromSchedule(context, monthIndex, currentBrigade)
        _uiState.update {
            it.copy(
                factHours = calcFact.toInt().toString(),
                nightShifts = calcNight.toInt().toString(),
                s4Shifts = calcDay.toInt().toString()
            )
        }
    }

    fun performCalculation() {
        val state = uiState.value
        val result = calculateSalary(state)
        if (result.error != null) {
            _uiState.update { it.copy(errorMessage = result.error, showResults = false) }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    showResults = true,
                    calculationResult = result,
                    effectiveFactText = result.effectiveFactText
                )
            }
        }
        saveCurrentMonthData()
    }

    fun saveToHistory(historyManager: HistoryManager) {
        val state = uiState.value
        val result = state.calculationResult ?: return
        historyManager.saveRecord(
            state.selectedMonthIndex,
            MonthlyNorms.list[state.selectedMonthIndex].name,
            result.totalClean,
            result.cleanToPay,
            result.avans
        )
    }

    private fun saveCurrentMonthData() {
        val monthIndex = uiState.value.selectedMonthIndex
        val state = uiState.value
        prefs.edit()
            .putString("${PreferenceKeys.NORM_PREFIX}$monthIndex", state.normHours)
            .putString("${PreferenceKeys.FACT_PREFIX}$monthIndex", state.factHours)
            .putString("${PreferenceKeys.NIGHT_PREFIX}$monthIndex", state.nightShifts)
            .putString("${PreferenceKeys.S4_PREFIX}$monthIndex", state.s4Shifts)
            .putString("${PreferenceKeys.ADV_PREFIX}$monthIndex", state.advanceShifts)
            .putString("${PreferenceKeys.PRAZDN_PREFIX}$monthIndex", state.prazdnHours)
            .putString("${PreferenceKeys.OTSUT_PREFIX}$monthIndex", state.zaOtsutstvuushego)
            .putString("${PreferenceKeys.KVART_PREFIX}$monthIndex", state.kvartalka)
            .putString("${PreferenceKeys.GAZ_PREFIX}$monthIndex", state.gazetaInput)
            .putString("${PreferenceKeys.POZ_PREFIX}$monthIndex", state.pozhertvovanjaInput)
            .putString("${PreferenceKeys.SUB_PREFIX}$monthIndex", state.subbotnikInput)
            .putString("${PreferenceKeys.SVOY_PREFIX}$monthIndex", state.zaSvoySchetInput)
            .putString("${PreferenceKeys.MMDETI_PREFIX}$monthIndex", state.mmDetiCountInput)
            .putString("${PreferenceKeys.CHILDREN_PREFIX}$monthIndex", state.childrenCountInput)
            .apply()
    }

    private fun calculateSalary(state: SalaryUiState): CalculationResultWithError {
        val okladBase = 1607.93
        val koefStazh = 0.25
        val vrednostKoef = 0.423125
        val koefNoch = 0.4
        val koefPrem = 0.45
        val vychetNaOdnogoRebenka = 63.0
        val bazovayaVelichina = 45.0

        val normVal = parseNonNegative(state.normHours)
        val factVal = parseNonNegative(state.factHours)
        val nShiftsVal = parseNonNegative(state.nightShifts)
        val s4ShiftsVal = parseNonNegative(state.s4Shifts)
        val prazdnVal = parseNonNegative(state.prazdnHours)
        val advShiftsVal = parseNonNegative(state.advanceShifts)
        val vOtsut = parseNonNegative(state.zaOtsutstvuushego)
        val vKvartal = parseNonNegative(state.kvartalka)
        val vGaz = parseNonNegative(state.gazetaInput)
        val vPoz = parseNonNegative(state.pozhertvovanjaInput)
        val vSub = parseNonNegative(state.subbotnikInput)
        val vZaSvoyShifts = parseNonNegative(state.zaSvoySchetInput)
        val childrenCount = parseNonNegative(state.childrenCountInput)
        val mmDetiCountVal = parseNonNegative(state.mmDetiCountInput)

        if (normVal <= 0.0) {
            return CalculationResultWithError(error = "Норма часов должна быть больше нуля")
        }

        val hoursZaSvoy = vZaSvoyShifts * 8.0
        val effectiveFactHours = maxOf(0.0, factVal - hoursZaSvoy)
        val effectiveFactText = if (vZaSvoyShifts > 0) " (-${hoursZaSvoy.toInt()} ч за свой счет)" else ""

        val okladReal = (okladBase / normVal) * effectiveFactHours
        val stazh = okladReal * koefStazh
        val vrednost = vrednostKoef * effectiveFactHours
        val nightHours = (nShiftsVal * 8.0) + (s4ShiftsVal * 2.0)
        val nochPay = (okladBase / normVal) * nightHours * koefNoch
        val prazdn = (okladBase / normVal) * prazdnVal

        val prevMonthIndex = (state.selectedMonthIndex - 1 + 12) % 12
        val defaultPrevNorm = MonthlyNorms.list[prevMonthIndex].norm.toString()
        val defaultPrevFact = MonthlyNorms.list[prevMonthIndex].fact.toString()
        val savedPrevNorm = prefs.getString("${PreferenceKeys.NORM_PREFIX}$prevMonthIndex", defaultPrevNorm) ?: defaultPrevNorm
        val savedPrevFact = prefs.getString("${PreferenceKeys.FACT_PREFIX}$prevMonthIndex", defaultPrevFact) ?: defaultPrevFact
        var prevNormVal = parseNonNegative(savedPrevNorm)
        if (prevNormVal <= 0.0) prevNormVal = MonthlyNorms.list[prevMonthIndex].norm
        val prevFactVal = parseNonNegative(savedPrevFact)
        val prem = (okladBase / prevNormVal) * prevFactVal * koefPrem

        val mmDeti = mmDetiCountVal * bazovayaVelichina

        val sumBeforePension = okladReal + stazh + vrednost + nochPay + prazdn + prem + vOtsut + vKvartal
        val pension = sumBeforePension * 0.06
        val dirty = sumBeforePension + pension + mmDeti
        val fszn = dirty * 0.01
        val prof = dirty * 0.01
        val childrenDeduction = vychetNaOdnogoRebenka * childrenCount
        val podohodnyBase = maxOf(0.0, dirty - childrenDeduction - mmDeti)
        val podohodny = podohodnyBase * 0.13
        val avans = (okladBase / normVal) * advShiftsVal * 8.0
        val totalClean = dirty - fszn - prof - podohodny - vGaz - vPoz - vSub
        val cleanToPay = totalClean - avans

        return CalculationResultWithError(
            okladReal = okladReal,
            stazh = stazh,
            vrednost = vrednost,
            nightHours = nightHours,
            nochPay = nochPay,
            prazdn = prazdn,
            prem = prem,
            mmDeti = mmDeti,
            sumBeforePension = sumBeforePension,
            pension = pension,
            dirty = dirty,
            fszn = fszn,
            prof = prof,
            childrenDeduction = childrenDeduction,
            podohodnyBase = podohodnyBase,
            podohodny = podohodny,
            avans = avans,
            totalClean = totalClean,
            cleanToPay = cleanToPay,
            effectiveFactText = effectiveFactText,
            error = null
        )
    }

    // ===== ДАННЫЕ КЛАССЫ ДЛЯ UI =====
    data class SalaryUiState(
        val selectedMonthIndex: Int = 5,
        val normHours: String = "",
        val factHours: String = "",
        val nightShifts: String = "",
        val s4Shifts: String = "",
        val advanceShifts: String = "",
        val prazdnHours: String = "0",
        val zaOtsutstvuushego: String = "",
        val kvartalka: String = "",
        val gazetaInput: String = "0",
        val pozhertvovanjaInput: String = "0",
        val subbotnikInput: String = "0",
        val zaSvoySchetInput: String = "0",
        val mmDetiCountInput: String = "0",
        val childrenCountInput: String = "2",
        val showResults: Boolean = false,
        val calculationResult: CalculationResultWithError? = null,
        val effectiveFactText: String = "",
        val errorMessage: String? = null
    )

    data class CalculationResultWithError(
        val okladReal: Double = 0.0,
        val stazh: Double = 0.0,
        val vrednost: Double = 0.0,
        val nightHours: Double = 0.0,
        val nochPay: Double = 0.0,
        val prazdn: Double = 0.0,
        val prem: Double = 0.0,
        val mmDeti: Double = 0.0,
        val sumBeforePension: Double = 0.0,
        val pension: Double = 0.0,
        val dirty: Double = 0.0,
        val fszn: Double = 0.0,
        val prof: Double = 0.0,
        val childrenDeduction: Double = 0.0,
        val podohodnyBase: Double = 0.0,
        val podohodny: Double = 0.0,
        val avans: Double = 0.0,
        val totalClean: Double = 0.0,
        val cleanToPay: Double = 0.0,
        val effectiveFactText: String = "",
        val error: String? = null
    )
}