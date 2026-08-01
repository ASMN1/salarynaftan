package com.example.salarynaftan

// Единый источник норм рабочего времени и умолчаний по месяцам.
// Раньше эти же числа были продублированы в двух местах —
// на экране "Зарплата" и на экране "График смен".

data class MonthData(
    val name: String,
    val norm: Double,
    val fact: Double,
    val defaultNightShifts: Double,
    val defaultS4Shifts: Double,
    val defaultAdvanceShifts: Double
)

object MonthlyNorms {
    // ===== СПИСОК МЕСЯЦЕВ В ИМЕНИТЕЛЬНОМ ПАДЕЖЕ (ДЛЯ КАЛЕНДАРЯ) =====
    val MONTH_NAMES_NOMINATIVE = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    val list: List<MonthData> = listOf(
        MonthData("Январь", 132.0, 144.0, 0.0, 0.0, 9.0),
        MonthData("Февраль", 140.0, 136.0, 0.0, 0.0, 8.0),
        MonthData("Март", 154.0, 152.0, 0.0, 0.0, 10.0),
        MonthData("Апрель", 145.0, 144.0, 0.0, 0.0, 9.0),
        MonthData("Май", 139.0, 144.0, 0.0, 0.0, 9.0),
        MonthData("Июнь", 154.0, 144.0, 6.0, 6.0, 9.0),
        MonthData("Июль", 153.0, 144.0, 6.0, 6.0, 9.0),
        MonthData("Август", 147.0, 151.5, 6.0, 7.0, 10.0),
        MonthData("Сентябрь", 154.0, 144.0, 6.0, 6.0, 9.0),
        MonthData("Октябрь", 154.0, 151.5, 6.0, 7.0, 9.0),
        MonthData("Ноябрь", 146.0, 144.0, 6.0, 6.0, 9.0),
        MonthData("Декабрь", 152.0, 144.0, 6.0, 6.0, 9.0)
    )
}