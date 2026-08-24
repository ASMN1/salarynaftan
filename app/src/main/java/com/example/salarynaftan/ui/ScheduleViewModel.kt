package com.example.salarynaftan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.salarynaftan.AlarmScheduler
import com.example.salarynaftan.ScheduleType
import com.example.salarynaftan.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

data class ScheduleUiState(
    val visibleMonth: YearMonth = YearMonth.now(),
    val viewingBrigade: Int = 1,
    val viewingScheduleType: ScheduleType = ScheduleType.GRAPH_1,
    val missedDays: Map<String, Set<Int>> = emptyMap(),
    val vacationDays: Map<String, Set<Int>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/** Owns schedule screen state and side effects; Compose only renders this state. */
class ScheduleViewModel(
    private val settings: SettingsManager,
    private val scheduler: AlarmScheduler,
    private val data: ScheduleDataCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(
        ScheduleUiState(
            viewingBrigade = settings.getBrigade(),
            viewingScheduleType = settings.getScheduleType()
        )
    )
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()
    private val saveMutex = Mutex()

    // Отмена предыдущей операции applyVacation при быстром повторном вызове (п.4.1 аудита).
    private var vacationJob: Job? = null

    init { loadMonth(_state.value.visibleMonth) }

    fun setVisibleMonth(month: YearMonth) {
        _state.update { it.copy(visibleMonth = month) }
        loadMonth(month)
    }

    fun switchScheduleType(type: ScheduleType) {
        if (_state.value.viewingScheduleType == type) return
        settings.setScheduleType(type)
        val brigade = settings.getBrigade()
        _state.update { it.copy(viewingScheduleType = type, viewingBrigade = brigade) }
        runCatching { scheduler.rescheduleAllAlarmsForBrigade(brigade) }
    }

    fun setViewingBrigade(brigade: Int) {
        val safe = brigade.coerceIn(1, _state.value.viewingScheduleType.brigadeCount)
        _state.update { it.copy(viewingBrigade = safe) }
    }

    fun missedDays(month: YearMonth): Set<Int> =
        _state.value.missedDays[key(month)] ?: emptySet()

    fun vacationDays(month: YearMonth): Set<Int> =
        _state.value.vacationDays[key(month)] ?: emptySet()

    fun toggleMissedDay(day: Int, month: YearMonth) {
        val current = missedDays(month).toMutableSet().apply {
            if (!add(day)) remove(day)
        }
        _state.update { it.copy(missedDays = it.missedDays + (key(month) to current)) }
        viewModelScope.launch {
            saveMutex.withLock { data.saveMissedDays(month, current) }
        }
    }

    fun applyVacation(from: LocalDate, to: LocalDate, remove: Boolean) {
        // Отменяем предыдущую операцию, чтобы исключить гонку записи в Room
        // при быстром повторном вызове (п.4.1 аудита).
        vacationJob?.cancel()
        val grouped = groupVacationDays(from, to)
        vacationJob = viewModelScope.launch {
            saveMutex.withLock {
                grouped.forEach { (month, days) ->
                    val monthKey = key(month)
                    if (remove) data.removeVacationDays(month, days)
                    else data.updateVacationDays(month, days)
                    val updated = if (remove) vacationDays(month) - days else vacationDays(month) + days
                    _state.update { it.copy(vacationDays = it.vacationDays + (monthKey to updated)) }
                }
            }
        }
    }

    private fun loadMonth(month: YearMonth) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { data.loadAnnotations(month) }
                .onSuccess { annotations ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            missedDays = it.missedDays + (key(month) to annotations.missedDays),
                            vacationDays = it.vacationDays + (key(month) to annotations.vacationDays)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private fun key(month: YearMonth): String = "${month.year}-${month.monthValue}"

    companion object {
        fun groupVacationDays(from: LocalDate, to: LocalDate): Map<YearMonth, Set<Int>> {
            val begin = minOf(from, to)
            val finish = maxOf(from, to)
            return buildMap {
                var date = begin
                while (!date.isAfter(finish)) {
                    val month = YearMonth.from(date)
                    put(month, (get(month).orEmpty() + date.dayOfMonth))
                    date = date.plusDays(1)
                }
            }
        }
    }
}