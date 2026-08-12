package com.example.salarynaftan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.salarynaftan.data.MonthSalaryEntity
import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.data.SalaryHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LegacyYearRecoveryViewModel(
    private val repository: SalaryRepository
) : ViewModel() {
    data class UiState(
        val months: List<MonthSalaryEntity> = emptyList(),
        val history: List<SalaryHistoryEntity> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val completed: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, completed = false) }
            runCatching {
                repository.getUnknownYearMonths() to repository.getUnknownYearHistory()
            }
                .onSuccess { (months, history) ->
                    _uiState.value = UiState(months = months, history = history)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Не удалось загрузить legacy-записи") }
                }
        }
    }

    fun assign(month: MonthSalaryEntity, yearText: String) {
        val year = LegacyYearInput.parse(yearText)
        if (year == null) {
            _uiState.update { it.copy(error = "Введите корректный год") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.assignLegacyMonthYear(month, year) }
                .onSuccess { reload() }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Не удалось назначить год") }
                }
        }
    }

    fun assignHistory(record: SalaryHistoryEntity, yearText: String) {
        val year = LegacyYearInput.parse(yearText)
        if (year == null) {
            _uiState.update { it.copy(error = "Введите корректный год") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.assignLegacyHistoryYear(record, year) }
                .onSuccess { reload() }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Не удалось назначить год истории") }
                }
        }
    }
}

object LegacyYearInput {
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..9999 }
}