package com.example.salarynaftan.data

import androidx.room.Entity
import com.example.salarynaftan.MonthSalaryEntityLike

@Entity(
    tableName = "month_salary",
    primaryKeys = ["year", "monthIndex"]
)
data class MonthSalaryEntity(
    val year: Int, // + например 2027
    val monthIndex: Int, // 0-11
    override val normHours: String = "",
    val prazdnHours: String = "0",
    val zaOtsutstvuushego: String = "",
    val kvartalka: String = "",
    val gazetaInput: String = "0",
    val pozhertvovanjaInput: String = "0",
    val subbotnikInput: String = "0",
    val mmDetiCountInput: String = "0",
    val childrenCountInput: String = "0",
    val stravitaInput: String = "0",
    val missedDays: String = "", // comma-separated day numbers
    val vacationDays: String = "" // comma-separated day numbers
) : MonthSalaryEntityLike
