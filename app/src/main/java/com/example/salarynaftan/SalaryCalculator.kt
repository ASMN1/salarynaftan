package com.example.salarynaftan

import java.time.YearMonth
import kotlin.math.max

/**
 * Чистая (без Android-зависимостей) логика расчёта зарплаты и итогов месяца.
 * Используется и ViewModel, и экраном «График» (MonthlyStatsCard), и тестами,
 * чтобы формула существовала в одном месте (DRY, устранение дублирования).
 */
object SalaryCalculator {

    const val VREDNOST_KOEF = 0.423125
    const val KOEF_NOCH = 0.4
    const val VYCHET_NA_ODNOGO_REBENKA = 63.0
    const val BAZOVAYA_VELICHINA = 45.0
    const val DEFAULT_PENSION_PERCENT = 6.0 // ППС по умолчанию, %

    /** Длительность одной смены (часы). */
    const val SHIFT_HOURS = 8.0

    /** Дополнительные ночные часы, начисляемые за дневную (16:00–00:00) смену. */
    const val DAY_SHIFT_NIGHT_BONUS_HOURS = 2.0

    /** Ставка взноса в ФСЗН и профсоюзный взнос (%). */
    const val SOCIAL_FUND_RATE = 0.01

    /** Ставка подоходного налога (%). */
    const val INCOME_TAX_RATE = 0.13

    // ---- Входные структуры (устранён God-object из сигнатуры) ----

    /** Данные месяца, вводимые пользователем (числовые, без привязки к UI). */
    data class MonthInput(
        val normHours: Double,
        val prazdnHours: Double,
        val zaOtsutstvuushego: Double,
        val kvartalka: Double,
        val gazetaInput: Double,
        val pozhertvovanjaInput: Double,
        val subbotnikInput: Double,
        val mmDetiCount: Double,
        val childrenCount: Double,
        val stravitaInput: Double
    )

    /** Внешние параметры расчёта — оклад, коэффициенты, бригада, невыходы, данные прошлого месяца. */
    data class CalcInputs(
        val okladBase: Double,
        val koefStazh: Double,
        val koefPrem: Double,
        val currentBrigade: Int,
        val currentMissed: Set<Int>,
        val currentVacation: Set<Int>,
        val prevMonthData: MonthSalaryEntityLike?,
        val prevMissed: Set<Int>,
        val prevVacation: Set<Int>,
        /** Тип графика (№1/№2) — влияет на длительность смены и ночные часы. */
        val scheduleType: ScheduleType = ScheduleType.GRAPH_1
    )

    /** Отработанные часы и смены за месяц с учётом отпусков/невыходов. */
    data class MonthStats(
        val workDays: Double = 0.0,
        val nightShifts: Double = 0.0,   // ночных смен (по 8 ч)
        val dayShifts: Double = 0.0,     // дневных смен (дают +2 ночных часа)
        val advanceShifts: Double = 0.0, // смен до 15-го (за вычетом ночных 15-го)
        val workDaysInt: Int = 0,
        val nightCount: Int = 0,
        val dayCount: Int = 0,
        val morningCount: Int = 0,
        val holidayHours: Double = 0.0
    )

    /**
     * Считает рабочие дни/смены месяца для заданной бригады с учётом невыходов
     * и отпусков. Пометка выходного дня (OFF) на загруженность не влияет.
     * Используется и в расчёте зарплаты, и на экране «График» — единый источник.
     */
    fun monthStats(
        year: Int,
        monthIndex: Int,
        brigade: Int,
        missedDays: Set<Int>,
        vacationDays: Set<Int>,
        scheduleType: ScheduleType = ShiftSchedule.currentScheduleType
    ): MonthStats {
        val yearMonth = YearMonth.of(year, monthIndex + 1)
        val shiftHours = scheduleType.shiftHours
        var workDays = 0.0
        var nightShifts = 0.0
        var dayShifts = 0.0
        var advanceShifts = 0.0
        var nightCount = 0
        var dayCount = 0
        var morningCount = 0
        var holidayHours = 0.0

        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day)
            val shift = ShiftSchedule.shiftFor(date, brigade, scheduleType)
            if (shift == ShiftType.OFF) continue
            if (day in missedDays || day in vacationDays) continue

            workDays += 1.0
            if (Holidays.isHoliday(date)) holidayHours += shiftHours
            when (shift) {
                ShiftType.NIGHT -> { nightShifts += 1.0; nightCount++ }
                ShiftType.DAY -> { dayShifts += 1.0; dayCount++ }
                ShiftType.MORNING -> morningCount++
                ShiftType.OFF -> {}
            }
            if (day < 15 || (day == 15 && shift != ShiftType.NIGHT)) {
                advanceShifts += 1.0
            }
        }

        return MonthStats(
            workDays = workDays,
            nightShifts = nightShifts,
            dayShifts = dayShifts,
            advanceShifts = advanceShifts,
            workDaysInt = workDays.toInt(),
            nightCount = nightCount,
            dayCount = dayCount,
            morningCount = morningCount,
            holidayHours = holidayHours
        )
    }

    /** Количество промаркированных невыходами/отпуском РАБОЧИХ дней. */
    private fun markedWorkDays(
        year: Int,
        monthIndex: Int,
        brigade: Int,
        missed: Set<Int>,
        vacation: Set<Int>,
        scheduleType: ScheduleType = ShiftSchedule.currentScheduleType
    ): Int {
        val yearMonth = YearMonth.of(year, monthIndex + 1)
        var count = 0
        for (day in 1..yearMonth.lengthOfMonth()) {
            if (day in missed || day in vacation) {
                val shift = ShiftSchedule.shiftFor(yearMonth.atDay(day), brigade, scheduleType)
                if (shift != ShiftType.OFF) count++
            }
        }
        return count
    }

    /**
     * Единая точка расчёта аванса за месяц (деньги). Используется на экране
     * «График» (MonthlyStatsCard) и в PDF-экспортёрах, чтобы формула
     * (оклад / норма × смен-до-15-го × 8 ч) существовала в одном месте.
     */
    fun advanceAmount(
        okladBase: Double,
        normHours: Double,
        shiftCountBefore15: Int,
        shiftHours: Double = ScheduleType.GRAPH_1.shiftHours
    ): Double =
        if (normHours > 0) (okladBase / normHours) * shiftCountBefore15 * shiftHours else 0.0

    /**
     * Полный расчёт зарплаты за месяц.
     *
     * @param year       год расчёта
     * @param monthIndex месяц расчёта (0–11)
     * @param monthData  вводимые пользователем данные месяца (норма, доплаты, удержания)
     * @param inputs     оклад, коэффициенты, бригада, невыходы текущего и прошлого месяца
     */
    fun calculate(
        year: Int,
        monthIndex: Int,
        monthData: MonthInput,
        inputs: CalcInputs,
        pensionPercent: Double = DEFAULT_PENSION_PERCENT
    ): CalculationResultWithError {
        val prevMonthIndex = (monthIndex - 1 + 12) % 12
        val prevYear = if (monthIndex == 0) year - 1 else year

        val scheduleType = inputs.scheduleType
        val shiftHours = scheduleType.shiftHours
        val dayShiftBonus = scheduleType.dayShiftNightBonusHours

        val stats = monthStats(year, monthIndex, inputs.currentBrigade, inputs.currentMissed, inputs.currentVacation, scheduleType)
        val factVal = stats.workDays * shiftHours
        val nShiftsVal = stats.nightShifts
        val s4ShiftsVal = stats.dayShifts
        val advShiftsVal = stats.advanceShifts

        val normVal = monthData.normHours
        if (normVal <= 0.0) {
            return CalculationResultWithError(error = "Норма часов должна быть больше нуля")
        }

        val okladReal = (inputs.okladBase / normVal) * factVal
        val stazh = okladReal * inputs.koefStazh
        val vrednost = VREDNOST_KOEF * factVal
        val nightHours = (nShiftsVal * shiftHours) + (s4ShiftsVal * dayShiftBonus)
        val nochPay = (inputs.okladBase / normVal) * nightHours * KOEF_NOCH

        // Праздничные часы всегда считаются автоматически из календаря по
        // реально отработанным дням (stats.holidayHours уже исключает невыходы
        // и отпуска). НЕ используем monthData.prazdnHours как источник: это
        // значение хранит полное число праздничных часов без учёта пропусков
        // (не пересчитывается при пометке дня), из-за чего праздничный день,
        // в который сотрудника не было на работе, всё равно добавлялся.
        val prazdnVal = stats.holidayHours
        val prazdn = (inputs.okladBase / normVal) * prazdnVal

        // Норма прошлого месяца: сохранённая (ручная) ИЛИ из справочника по году
        val defaultPrevNorm = MonthlyNorms.norm(prevYear, prevMonthIndex).toString()
        val savedPrevNorm = inputs.prevMonthData?.normHours?.takeIf { it.isNotEmpty() } ?: defaultPrevNorm
        var prevNormVal = parseNonNegative(savedPrevNorm)
        if (prevNormVal <= 0.0) prevNormVal = MonthlyNorms.norm(prevYear, prevMonthIndex)

        // Факт прошлого месяца: вычисляем реальные часы по графику (без невыходов)
        // вместо хардкода из MonthlyNorms.list — корректно для любого года.
        val prevStatsFull = monthStats(prevYear, prevMonthIndex, inputs.currentBrigade, emptySet(), emptySet(), scheduleType)
        val defaultPrevFact = prevStatsFull.workDays * shiftHours
        val prevMissedHours = markedWorkDays(prevYear, prevMonthIndex, inputs.currentBrigade, inputs.prevMissed, inputs.prevVacation, scheduleType) * shiftHours
        val premFactVal = maxOf(0.0, defaultPrevFact - prevMissedHours)
        val prem = (inputs.okladBase / prevNormVal) * premFactVal * inputs.koefPrem

        val mmDeti = monthData.mmDetiCount * BAZOVAYA_VELICHINA

        val sumBeforePension = okladReal + stazh + vrednost + nochPay + prazdn + prem +
                monthData.zaOtsutstvuushego + monthData.kvartalka
        val pension = sumBeforePension * (pensionPercent.coerceIn(0.0, 100.0) / 100.0)
        val dirty = sumBeforePension + pension + mmDeti
        val fszn = dirty * SOCIAL_FUND_RATE
        val prof = dirty * SOCIAL_FUND_RATE
        val childrenDeduction = VYCHET_NA_ODNOGO_REBENKA * monthData.childrenCount
        val podohodnyBase = maxOf(0.0, dirty - childrenDeduction - mmDeti)
        val podohodny = podohodnyBase * INCOME_TAX_RATE
        val avans = advanceAmount(inputs.okladBase, normVal, advShiftsVal.toInt(), shiftHours)
        val totalClean = dirty - fszn - prof - podohodny -
                monthData.gazetaInput - monthData.pozhertvovanjaInput -
                monthData.subbotnikInput - monthData.stravitaInput
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
            error = null
        )
    }
}

/**
 * Лёгкий тип данных прошлого месяца, чтобы SalaryCalculator не зависел
 * от сущности Room (MonthSalaryEntity) и оставался чистым/тестируемым.
 */
interface MonthSalaryEntityLike {
    val normHours: String?
}