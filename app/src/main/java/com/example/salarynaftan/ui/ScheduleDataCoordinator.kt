package com.example.salarynaftan.ui

import com.example.salarynaftan.data.SalaryRepository
import java.time.YearMonth

/** Persistence use-case for schedule annotations; intentionally UI-framework free. */
class ScheduleDataCoordinator(private val repository: SalaryRepository) {
    suspend fun loadAnnotations(month: YearMonth): ScheduleAnnotations =
        ScheduleAnnotations(
            missedDays = repository.getMissedDays(month.year, month.monthValue - 1),
            vacationDays = repository.getVacationDays(month.year, month.monthValue - 1)
        )

    suspend fun saveMissedDays(month: YearMonth, days: Set<Int>) {
        repository.saveMissedDays(month.year, month.monthValue - 1, days)
    }

    suspend fun updateVacationDays(month: YearMonth, days: Set<Int>) {
        val current = repository.getVacationDays(month.year, month.monthValue - 1).toMutableSet()
        current.addAll(days)
        repository.saveVacationDays(month.year, month.monthValue - 1, current)
    }

    suspend fun removeVacationDays(month: YearMonth, days: Set<Int>) {
        val current = repository.getVacationDays(month.year, month.monthValue - 1).toMutableSet()
        current.removeAll(days)
        repository.saveVacationDays(month.year, month.monthValue - 1, current)
    }
}

data class ScheduleAnnotations(
    val missedDays: Set<Int>,
    val vacationDays: Set<Int>
)