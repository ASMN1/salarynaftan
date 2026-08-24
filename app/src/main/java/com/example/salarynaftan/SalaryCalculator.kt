package com.example.salarynaftan

import java.time.YearMonth
import kotlin.math.max

/**
 * Чистая (без Android-зависимостей) логика расчёта зарплаты и итогов месяца.
 * Используется и ViewModel, и экраном «График» (MonthlyStatsCard), и тестами,
 * чтобы формула существовала в одном месте (DRY, устранение дублирования).
 */
object SalaryCalculator {

    // Доплата за вредность (п.3.4): ВРЕДНОСТЬ = БАЗ.СТАВКА-1-РАЗРЯДА × КОЭФ.КЛАССА / 100 × ФАКТ.ЧАС
    // (формула из Зарплата6.xlsx). Базовая ставка 1 разряда — константа завода.
    const val BASE_RATE_RANK1 = 302.24
    const val KOEF_NOCH = 0.4
    const val VYCHET_NA_ODNOGO_REBENKA = 63.0
    const val BAZOVAYA_VELICHINA = 45.0
    const val DEFAULT_PENSION_PERCENT = 6.0 // ППС по умолчанию, %

    // Коэффициенты по умолчанию для новых надбавок (совпадают с Зарплата6.xlsx).
    const val DEFAULT_HARM_CLASS_COEF = 0.14   // 2 класс вредности
    const val DEFAULT_PROF_COEF = 0.32         // профмастерство (%) → 0.32
    const val DEFAULT_INTENS_COEF = 0.005      // интенсивность труда (%) → 0.5%
    const val DEFAULT_BASE_RATE_RANK = 574.26  // базовая ставка 6 разряда (по умолчанию)

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
        val zaOtsutstvuushego: Double,
        val kvartalka: Double,
        val gazetaInput: Double,
        val pozhertvovanjaInput: Double,
        val subbotnikInput: Double,
        val mmDetiCount: Double,
        val childrenCount: Double,
        val stravitaInput: Double,
        val inyeVyplaty: Double = 0.0,
        val inyeUderzhanija: Double = 0.0
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
        val scheduleType: ScheduleType = ScheduleType.GRAPH_1,
        /** Класс вредности (1/2/3 → 0.20/0.14/0.10). */
        val harmClassCoef: Double = DEFAULT_HARM_CLASS_COEF,
        /** Профмастерство (%, в долях: 32% → 0.32). */
        val profCoef: Double = DEFAULT_PROF_COEF,
        /** Интенсивность труда (%, в долях: 0.5% → 0.005). */
        val intensCoef: Double = DEFAULT_INTENS_COEF,
        /** Базовая ставка выбранного разряда (для профмастерства). */
        val baseRate: Double = DEFAULT_BASE_RATE_RANK
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
        scheduleType: ScheduleType = ScheduleType.GRAPH_1
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
        scheduleType: ScheduleType
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

        // Валидация входных параметров от SettingsManager: защита от NaN/Infinity
        // (п.4.3 аудита). Если в DataStore попадёт невалидное значение, расчёт
        // не должен распространять NaN в UI. Вынесено в отдельные методы (п.3.1).
        validateInputs(inputs)?.let { return CalculationResultWithError(error = it) }

        // Округление промежуточных значений до копеек на каждом шаге (п.5.1 аудита),
        // чтобы накопление погрешности не дало расхождение с расчётным листком завода.
        val okladReal = calculateOkladScale(inputs, normVal, factVal)
        val stazh = calculateStazh(inputs, okladReal)
        val vrednost = calculateVrednost(inputs.harmClassCoef, factVal)
        val nightHours = (nShiftsVal * shiftHours) + (s4ShiftsVal * dayShiftBonus)
        val nochPay = calculateNightPay(inputs, normVal, nightHours)

        // Праздничные часы всегда считаются автоматически из календаря по
        // реально отработанным дням (stats.holidayHours уже исключает невыходы
        // и отпуска). НЕ используем monthData.prazdnHours как источник: это
        // значение хранит полное число праздничных часов без учёта пропусков
        // (не пересчитывается при пометке дня), из-за чего праздничный день,
        // в который сотрудника не было на работе, всё равно добавлялся.
        val prazdn = calculateHolidayPay(inputs, normVal, stats.holidayHours)

        // Профмастерство: (оклад / норма) × %проф × факт-часы (из Зарплата6.xlsx).
        val profMasterstvo = calculateProfMasterstvo(inputs, normVal, factVal)
        // Интенсивность труда: фактический оклад × %интенсивность (из Зарплата6.xlsx).
        val intensyvnost = calculateIntensyvnost(inputs, okladReal)

        val prem = calculatePremium(year, monthIndex, inputs, scheduleType, shiftHours)
        val mmDeti = calculateMmDeti(monthData)

        val sumBeforePension = MoneyFormatter.round(okladReal + stazh + vrednost + nochPay + prazdn +
                profMasterstvo + intensyvnost + prem +
                monthData.zaOtsutstvuushego + monthData.kvartalka + monthData.inyeVyplaty)
        val pension = MoneyFormatter.round(sumBeforePension * (pensionPercent.coerceIn(0.0, 100.0) / 100.0))
        val dirty = MoneyFormatter.round(sumBeforePension + pension + mmDeti)
        val fszn = socialFunds(dirty)
        val prof = socialFunds(dirty)
        val childrenDeduction = calculateChildrenDeduction(monthData)
        val podohodnyBase = maxOf(0.0, dirty - childrenDeduction - mmDeti)
        val podohodny = MoneyFormatter.round(podohodnyBase * INCOME_TAX_RATE)
        val avans = calculateAvans(inputs, normVal, advShiftsVal.toInt(), shiftHours)
        val totalClean = MoneyFormatter.round(dirty - fszn - prof - podohodny -
                monthData.gazetaInput - monthData.pozhertvovanjaInput -
                monthData.subbotnikInput - monthData.stravitaInput - monthData.inyeUderzhanija)
        val cleanToPay = MoneyFormatter.round(totalClean - avans)

        return CalculationResultWithError(
            okladReal = okladReal,
            stazh = stazh,
            vrednost = vrednost,
            nightHours = nightHours,
            nochPay = nochPay,
            prazdn = prazdn,
            prem = prem,
            profMasterstvo = profMasterstvo,
            intensyvnost = intensyvnost,
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

    // ===== Приватные шаги расчёта (п.3.1 аудита): разбивка длинного calculate =====

    /** Валидация входных параметров; возвращает текст ошибки или null. */
    private fun validateInputs(inputs: CalcInputs): String? {
        if (!inputs.okladBase.isFinite() || inputs.okladBase < 0) return "Некорректный оклад"
        if (!inputs.koefStazh.isFinite() || inputs.koefStazh < 0) return "Некорректный коэффициент стажа"
        if (!inputs.koefPrem.isFinite() || inputs.koefPrem < 0) return "Некорректный коэффициент премии"
        if (!inputs.harmClassCoef.isFinite() || inputs.harmClassCoef < 0) return "Некорректный класс вредности"
        if (!inputs.profCoef.isFinite() || inputs.profCoef < 0) return "Некорректный процент профмастерства"
        if (!inputs.intensCoef.isFinite() || inputs.intensCoef < 0) return "Некорректный процент интенсивности"
        return null
    }

    /** Оклад за фактически отработанные часы (оклад / норма × факт). */
    private fun calculateOkladScale(inputs: CalcInputs, normVal: Double, factVal: Double): Double =
        MoneyFormatter.round((inputs.okladBase / normVal) * factVal)

    /** Доплата за стаж (оклад × коэффициент стажа). */
    private fun calculateStazh(inputs: CalcInputs, okladReal: Double): Double =
        MoneyFormatter.round(okladReal * inputs.koefStazh)

    /** Доплата за вредность: (БАЗ.СТАВКА-1-РАЗРЯДА × коэф.класса / 100) × факт-часы. */
    private fun calculateVrednost(harmClassCoef: Double, factVal: Double): Double =
        MoneyFormatter.round(((BASE_RATE_RANK1 * harmClassCoef) / 100.0) * factVal)

    /** Профмастерство: (БАЗ.СТАВКА разряда / норма) × %проф × факт-часы. */
    private fun calculateProfMasterstvo(inputs: CalcInputs, normVal: Double, factVal: Double): Double =
        MoneyFormatter.round((inputs.baseRate / normVal) * inputs.profCoef * factVal)

    /** Интенсивность труда: фактический оклад × %интенсивность. */
    private fun calculateIntensyvnost(inputs: CalcInputs, okladReal: Double): Double =
        MoneyFormatter.round(okladReal * inputs.intensCoef)

    /** Доплата за ночные часы (оклад / норма × ночные часы × коэффициент). */
    private fun calculateNightPay(inputs: CalcInputs, normVal: Double, nightHours: Double): Double =
        MoneyFormatter.round((inputs.okladBase / normVal) * nightHours * KOEF_NOCH)

    /** Оплата праздничных часов (оклад / норма × праздничные часы). */
    private fun calculateHolidayPay(inputs: CalcInputs, normVal: Double, holidayHours: Double): Double =
        MoneyFormatter.round((inputs.okladBase / normVal) * holidayHours)

    /** Премия за прошлый месяц (оклад / норма прошлого × факт прошлого × коэф. премии). */
    private fun calculatePremium(
        year: Int,
        monthIndex: Int,
        inputs: CalcInputs,
        scheduleType: ScheduleType,
        shiftHours: Double
    ): Double {
        val prevMonthIndex = (monthIndex - 1 + 12) % 12
        val prevYear = if (monthIndex == 0) year - 1 else year
        // Норма прошлого месяца: сохранённая (ручная) ИЛИ из справочника по году.
        val defaultPrevNorm = MonthlyNorms.norm(prevYear, prevMonthIndex, scheduleType).toString()
        val savedPrevNorm = inputs.prevMonthData?.normHours?.takeIf { it.isNotEmpty() } ?: defaultPrevNorm
        var prevNormVal = parseNonNegative(savedPrevNorm)
        if (prevNormVal <= 0.0) prevNormVal = MonthlyNorms.norm(prevYear, prevMonthIndex, scheduleType)

        // Факт прошлого месяца: реальные часы по графику (без невыходов).
        val prevStatsFull = monthStats(prevYear, prevMonthIndex, inputs.currentBrigade, emptySet(), emptySet(), scheduleType)
        val defaultPrevFact = prevStatsFull.workDays * shiftHours
        val prevMissedHours = markedWorkDays(prevYear, prevMonthIndex, inputs.currentBrigade, inputs.prevMissed, inputs.prevVacation, scheduleType) * shiftHours
        val premFactVal = maxOf(0.0, defaultPrevFact - prevMissedHours)
        return MoneyFormatter.round((inputs.okladBase / prevNormVal) * premFactVal * inputs.koefPrem)
    }

    /** Доплата за детей (число базовых величин × базовая величина). */
    private fun calculateMmDeti(monthData: MonthInput): Double =
        MoneyFormatter.round(monthData.mmDetiCount * BAZOVAYA_VELICHINA)

    /** Взнос в ФСЗН/профсоюз (не округляется до копеек — контракт тестов). */
    private fun socialFunds(dirty: Double): Double = dirty * SOCIAL_FUND_RATE

    /** Вычет на детей (число детей × вычет на одного). */
    private fun calculateChildrenDeduction(monthData: MonthInput): Double =
        VYCHET_NA_ODNOGO_REBENKA * monthData.childrenCount

    /** Аванс за месяц (оклад / норма × смен-до-15-го × часы смены). */
    private fun calculateAvans(inputs: CalcInputs, normVal: Double, shiftCountBefore15: Int, shiftHours: Double): Double =
        MoneyFormatter.round(advanceAmount(inputs.okladBase, normVal, shiftCountBefore15, shiftHours))
}

/**
 * Лёгкий тип данных прошлого месяца, чтобы SalaryCalculator не зависел
 * от сущности Room (MonthSalaryEntity) и оставался чистым/тестируемым.
 */
interface MonthSalaryEntityLike {
    val normHours: String?
}