package com.example.salarynaftan

import androidx.lifecycle.SavedStateHandle
import com.example.salarynaftan.data.MonthSalaryEntity
import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.ui.SalaryCalculatorViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты SalaryCalculatorViewModel (п.6.6): валидация входных данных,
 * загрузка месяца, гонка сохранения при быстром переключении месяца.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SalaryCalculatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var settingsManager: SettingsManager
    private lateinit var salaryRepository: SalaryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsManager = mockk(relaxed = true)
        salaryRepository = mockk(relaxed = true)

        // Дефолтные настройки для расчёта
        every { settingsManager.getSalary() } returns 1607.93
        every { settingsManager.getStazhKoef() } returns 0.25
        every { settingsManager.getPremiumCoef() } returns 0.45
        every { settingsManager.getBrigade() } returns 1
        every { settingsManager.getScheduleType() } returns ScheduleType.GRAPH_1
        every { settingsManager.getPpsPercent() } returns 6.0
        every { settingsManager.getSelectedMonthIndex() } returns 5

        // Пустые данные по умолчанию
        coEvery { salaryRepository.getMonthData(any(), any()) } returns null
        coEvery { salaryRepository.getMissedDays(any(), any()) } returns emptySet()
        coEvery { salaryRepository.getVacationDays(any(), any()) } returns emptySet()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SalaryCalculatorViewModel =
        SalaryCalculatorViewModel(
            savedStateHandle = SavedStateHandle(),
            settingsManager = settingsManager,
            salaryRepository = salaryRepository
        )

    @Test
    fun `init loads month data and sets norm from MonthlyNorms for graph 1`() = runTest(dispatcher) {
        val vm = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        // Июнь (индекс 5) 2026: норма из справочника
        assertEquals(5, state.selectedMonthIndex)
        assertEquals(2026, state.selectedYear)
        assertNotNull(state.normHours)
        assertTrue(state.normHours.isNotEmpty())
    }

    @Test
    fun `updateField sanitizes non-numeric input`() = runTest(dispatcher) {
        val vm = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, "12abc,5")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("12,5", vm.uiState.value.kvartalka)
    }

    @Test
    fun `performCalculation with valid data shows results`() = runTest(dispatcher) {
        val vm = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        // Норма для Графика №1 берётся из справочника — она валидна.
        vm.performCalculation()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showResults)
        assertNull(state.errorMessage)
        assertNotNull(state.calculationResult)
    }

    @Test
    fun `selectMonth saves previous month data before switching`() = runTest(dispatcher) {
        val vm = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, "100")
        vm.selectMonth(6) // Июль
        dispatcher.scheduler.advanceUntilIdle()

        // Сохранение должно было вызваться с данными ИЮНЯ (индекс 5), а не июля.
        coVerify(exactly = 1) {
            salaryRepository.saveMonthData(match<MonthSalaryEntity> { it.monthIndex == 5 && it.kvartalka == "100" })
        }
        assertEquals(6, vm.uiState.value.selectedMonthIndex)
    }

    @Test
    fun `rapid month switching does not lose data`() = runTest(dispatcher) {
        val vm = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.updateField(SalaryCalculatorViewModel.SalaryField.KVARTALKA, "50")
        vm.selectMonth(6)
        vm.selectMonth(7)
        vm.selectMonth(8)
        dispatcher.scheduler.advanceUntilIdle()

        // Последнее сохранение — за июль (индекс 7), т.к. июнь (6) был отменён.
        coVerify {
            salaryRepository.saveMonthData(match<MonthSalaryEntity> { it.monthIndex == 7 && it.kvartalka == "50" })
        }
        assertEquals(8, vm.uiState.value.selectedMonthIndex)
    }
}