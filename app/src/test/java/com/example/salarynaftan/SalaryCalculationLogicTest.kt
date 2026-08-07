package com.example.salarynaftan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты расчёта зарплаты.
 * Используют тот же чистый SalaryCalculator, что и приложение, — формула
 * существует в одном месте (устранено дублирование из ViewModel).
 */
class SalaryCalculationLogicTest {

    private fun calcInputs(
        okladBase: Double = 1607.93,
        koefStazh: Double = 0.25,
        koefPrem: Double = 0.45,
        currentBrigade: Int = 1
    ) = SalaryCalculator.CalcInputs(
        okladBase = okladBase,
        koefStazh = koefStazh,
        koefPrem = koefPrem,
        currentBrigade = currentBrigade,
        currentMissed = emptySet(),
        currentVacation = emptySet(),
        prevMonthData = null,
        prevMissed = emptySet(),
        prevVacation = emptySet()
    )

    private fun monthInput(
        normHours: String = "132",
        prazdnHours: String = "0",
        zaOtsutstvuushego: String = "0",
        kvartalka: String = "0",
        gazetaInput: String = "0",
        pozhertvovanjaInput: String = "0",
        subbotnikInput: String = "0",
        childrenCount: String = "0",
        mmDetiCount: String = "0",
        stravitaInput: String = "0"
    ) = SalaryCalculator.MonthInput(
        normHours = parseNonNegative(normHours),
        prazdnHours = parseNonNegative(prazdnHours),
        zaOtsutstvuushego = parseNonNegative(zaOtsutstvuushego),
        kvartalka = parseNonNegative(kvartalka),
        gazetaInput = parseNonNegative(gazetaInput),
        pozhertvovanjaInput = parseNonNegative(pozhertvovanjaInput),
        subbotnikInput = parseNonNegative(subbotnikInput),
        mmDetiCount = parseNonNegative(mmDetiCount),
        childrenCount = parseNonNegative(childrenCount),
        stravitaInput = parseNonNegative(stravitaInput)
    )

    private fun performCalculate(
        month: SalaryCalculator.MonthInput,
        inputs: SalaryCalculator.CalcInputs,
        year: Int = 2027,
        monthIndex: Int = 0 // Январь
    ): CalculationResultWithError {
        return SalaryCalculator.calculate(
            year = year,
            monthIndex = monthIndex,
            monthData = month,
            inputs = inputs,
            pensionPercent = 6.0
        )
    }

    @Test
    fun `calculation with valid inputs succeeds`() {
        val result = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        assertNull(result.error)
    }

    // Внимание: полный расчёт учитывает график смен, поэтому okladReal
    // равен okladBase только условно. Здесь проверяем сам факт корректной работы.
    @Test
    fun `calculation produces non-negative amounts`() {
        val result = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertNotNull(result)
        check(!result.okladReal.isNaN())
        check(result.okladReal >= 0.0)
    }

    @Test
    fun `stazh is okladReal times koefStazh`() {
        val result = performCalculate(monthInput(normHours = "132"), calcInputs(koefStazh = 0.25))
        assertEquals(result.okladReal * 0.25, result.stazh, 0.01)
    }

    @Test
    fun `pension is 6 percent of sumBeforePension`() {
        val result = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        assertEquals(result.sumBeforePension * 0.06, result.pension, 0.01)
    }

    @Test
    fun `children deduction reduces tax base`() {
        val resultWithChildren = performCalculate(monthInput(normHours = "132", prazdnHours = "8", childrenCount = "2"), calcInputs())
        val resultWithout = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        org.junit.Assert.assertTrue(resultWithChildren.podohodnyBase < resultWithout.podohodnyBase)
    }

    @Test
    fun `fszn and prof are each 1 percent of dirty`() {
        val result = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        assertEquals(result.dirty * 0.01, result.fszn, 0.001)
        assertEquals(result.dirty * 0.01, result.prof, 0.001)
    }

    @Test
    fun `gazeta deduction reduces totalClean`() {
        val withGaz = performCalculate(monthInput(normHours = "132", prazdnHours = "8", gazetaInput = "50"), calcInputs())
        val withoutGaz = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        assertEquals(withoutGaz.totalClean - 50.0, withGaz.totalClean, 0.01)
    }

    @Test
    fun `mmDeti adds 45 per child`() {
        val result = performCalculate(monthInput(normHours = "132", prazdnHours = "8", mmDetiCount = "2"), calcInputs())
        assertEquals(90.0, result.mmDeti, 0.01)
    }

    @Test
    fun `kvartalka increases sumBeforePension`() {
        val withKvart = performCalculate(monthInput(normHours = "132", prazdnHours = "8", kvartalka = "200"), calcInputs())
        val withoutKvart = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        org.junit.Assert.assertTrue(withKvart.sumBeforePension > withoutKvart.sumBeforePension)
    }

    @Test
    fun `podohodny is 13 percent of base`() {
        val result = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        assertEquals(result.podohodnyBase * 0.13, result.podohodny, 0.01)
    }

    @Test
    fun `stravita deduction reduces totalClean`() {
        val withStrav = performCalculate(monthInput(normHours = "132", prazdnHours = "8", stravitaInput = "30"), calcInputs())
        val withoutStrav = performCalculate(monthInput(normHours = "132", prazdnHours = "8"), calcInputs())
        assertEquals(withoutStrav.totalClean - 30.0, withStrav.totalClean, 0.01)
    }

    @Test
    fun `zero norm yields an error`() {
        val result = performCalculate(monthInput(normHours = "0"), calcInputs())
        assertNotNull(result.error)
    }
}