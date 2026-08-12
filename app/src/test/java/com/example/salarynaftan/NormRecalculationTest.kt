package com.example.salarynaftan

import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Пересчёт норм 40-часовой рабочей недели (пятидневка) по методике Минтруда РБ:
 *   норма = 8 × (рабочие дни) − (предпраздничные часы)
 * где рабочие дни = календарные − субботы − воскресенья − праздники.
 * Предпраздничный день (рабочий день, непосредственно предшествующий празднику)
 * сокращается на 1 час. Если празднику предшествует выходной — сокращения нет.
 */
class NormRecalculationTest {

    // Даты Радуницы (второй вторник после православной Пасхи) по годам.
    private val radunitsaByYear = mapOf(
        2026 to LocalDate.of(2026, 4, 21),
        2027 to LocalDate.of(2027, 5, 11),
        2028 to LocalDate.of(2028, 4, 25),
        2029 to LocalDate.of(2029, 4, 17),
        2030 to LocalDate.of(2030, 5, 7),
        2031 to LocalDate.of(2031, 4, 28),
        2032 to LocalDate.of(2032, 5, 18),
        2033 to LocalDate.of(2033, 5, 10),
        2034 to LocalDate.of(2034, 4, 25),
        2035 to LocalDate.of(2035, 5, 15),
        2036 to LocalDate.of(2036, 5, 6),
        2037 to LocalDate.of(2037, 4, 21),
        2038 to LocalDate.of(2038, 5, 11),
        2039 to LocalDate.of(2039, 4, 26),
        2040 to LocalDate.of(2040, 5, 15)
    )

    // Фиксированные праздники (число, месяц)
    private val fixedHolidays = listOf(
        1 to 1, 2 to 1, 7 to 1, 8 to 3, 1 to 5, 9 to 5, 3 to 7, 7 to 11, 25 to 12
    )

    private fun isHoliday(d: LocalDate): Boolean {
        val md = d.monthValue to d.dayOfMonth
        if (fixedHolidays.contains(md)) return true
        return radunitsaByYear[d.year] == d
    }

    private fun monthNorm(year: Int, month: Int): Int {
        val ym = YearMonth.of(year, month)
        var workDays = 0
        var preHolidayHours = 0
        for (day in 1..ym.lengthOfMonth()) {
            val d = ym.atDay(day)
            if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) continue
            if (isHoliday(d)) continue
            workDays++
            // Предпраздничный: следующий день — праздник, и он рабочий (не сб/вс)
            val next = d.plusDays(1)
            if (isHoliday(next) && next.dayOfWeek != DayOfWeek.SATURDAY && next.dayOfWeek != DayOfWeek.SUNDAY) {
                preHolidayHours++
            }
        }
        return workDays * 8 - preHolidayHours
    }

    @Test
    fun `print all norms 2026-2040`() {
        val sb = StringBuilder()
        for (year in 2026..2040) {
            val norms = (1..12).map { monthNorm(year, it) }
            sb.append("$year: ")
            sb.append(norms.joinToString(", "))
            sb.append("  | год=${norms.sum()}\n")
        }
        println("===== ПЕРЕСЧЁТ ПО МЕТОДИКЕ МИНТРУДА (40-час, пятидневка) =====")
        println(sb.toString())
    }
}