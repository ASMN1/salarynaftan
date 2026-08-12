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
        monthIndex: Int = 0
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
        val result = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertNull(result.error)
    }

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
        val result = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertEquals(result.sumBeforePension * 0.06, result.pension, 0.01)
    }

    @Test
    fun `children deduction reduces tax base`() {
        val resultWithChildren = performCalculate(monthInput(normHours = "132", childrenCount = "2"), calcInputs())
        val resultWithout = performCalculate(monthInput(normHours = "132"), calcInputs())
        org.junit.Assert.assertTrue(resultWithChildren.podohodnyBase < resultWithout.podohodnyBase)
    }

    @Test
    fun `fszn and prof are each 1 percent of dirty`() {
        val result = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertEquals(result.dirty * 0.01, result.fszn, 0.001)
        assertEquals(result.dirty * 0.01, result.prof, 0.001)
    }

    @Test
    fun `gazeta deduction reduces totalClean`() {
        val withGaz = performCalculate(monthInput(normHours = "132", gazetaInput = "50"), calcInputs())
        val withoutGaz = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertEquals(withoutGaz.totalClean - 50.0, withGaz.totalClean, 0.01)
    }

    @Test
    fun `mmDeti adds 45 per child`() {
        val result = performCalculate(monthInput(normHours = "132", mmDetiCount = "2"), calcInputs())
        assertEquals(90.0, result.mmDeti, 0.01)
    }

    @Test
    fun `kvartalka increases sumBeforePension`() {
        val withKvart = performCalculate(monthInput(normHours = "132", kvartalka = "200"), calcInputs())
        val withoutKvart = performCalculate(monthInput(normHours = "132"), calcInputs())
        org.junit.Assert.assertTrue(withKvart.sumBeforePension > withoutKvart.sumBeforePension)
    }

    @Test
    fun `podohodny is 13 percent of base`() {
        val result = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertEquals(result.podohodnyBase * 0.13, result.podohodny, 0.01)
    }

    @Test
    fun `stravita deduction reduces totalClean`() {
        val withStrav = performCalculate(monthInput(normHours = "132", stravitaInput = "30"), calcInputs())
        val withoutStrav = performCalculate(monthInput(normHours = "132"), calcInputs())
        assertEquals(withoutStrav.totalClean - 30.0, withStrav.totalClean, 0.01)
    }

    @Test
    fun `zero norm yields an error`() {
        val result = performCalculate(monthInput(normHours = "0"), calcInputs())
        assertNotNull(result.error)
    }

    // Праздничный день, в который сотрудника не было на работе (отпуск или
    // невыход), НЕ должен начисляться как праздничный. Праздничные часы
    // считаются автоматически в SalaryCalculator через stats.holidayHours.
    @Test
    fun `holiday hours are skipped when the holiday day is missed or vacation`() {
        var foundHoliday = false
        for (year in 2026..2027) {
            for (monthIndex in 0..11) {
                val holidayDays = (1..java.time.YearMonth.of(year, monthIndex + 1).lengthOfMonth())
                    .filter { Holidays.isHoliday(java.time.YearMonth.of(year, monthIndex + 1).atDay(it)) }
                    .filter {
                        ShiftSchedule.shiftFor(java.time.YearMonth.of(year, monthIndex + 1).atDay(it), 1) != ShiftType.OFF
                    }
                if (holidayDays.isEmpty()) continue

                foundHoliday = true
                val allMarked = holidayDays.toSet()

                val withAllHolidaysMarked = performCalculate(
                    month = monthInput(normHours = "170"),
                    inputs = calcInputs().let { h ->
                        SalaryCalculator.CalcInputs(
                            okladBase = h.okladBase, koefStazh = h.koefStazh, koefPrem = h.koefPrem,
                            currentBrigade = h.currentBrigade,
                            currentMissed = h.currentMissed, currentVacation = allMarked,
                            prevMonthData = h.prevMonthData, prevMissed = h.prevMissed, prevVacation = h.prevVacation
                        )
                    },
                    year = year, monthIndex = monthIndex
                )

                assertEquals(
                    "Праздничные часы должны быть 0, если все праздничные дни — отпуск (year=$year, month=$monthIndex)",
                    0.0, withAllHolidaysMarked.prazdn, 0.01
                )
                break
            }
            if (foundHoliday) break
        }
        org.junit.Assert.assertTrue("Не найден ни один праздничный рабочий день за 2026-2027", foundHoliday)
    }
}