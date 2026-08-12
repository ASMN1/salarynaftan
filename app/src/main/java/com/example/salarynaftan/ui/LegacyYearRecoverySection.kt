package com.example.salarynaftan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarynaftan.data.MonthSalaryEntity
import com.example.salarynaftan.data.SalaryHistoryEntity
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LegacyYearRecoverySection(
    primary: Color,
    viewModel: LegacyYearRecoveryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    if (state.months.isEmpty() && state.history.isEmpty() && state.error == null) return
    val years = remember { mutableStateMapOf<Int, String>() }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Старые записи без года", fontSize = 16.sp)
            Text("Укажите год вручную — приложение не подставляет год устройства автоматически.", fontSize = 12.sp)
            state.error?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            state.months.forEach { month ->
                LegacyMonthRow(month, years[month.monthIndex] ?: "", primary) { value ->
                    years[month.monthIndex] = value
                    viewModel.assign(month, value)
                }
            }
            state.history.forEach { record ->
                LegacyHistoryRow(record, years[-record.monthIndex - 1] ?: "") { value ->
                    years[-record.monthIndex - 1] = value
                    viewModel.assignHistory(record, value)
                }
            }
        }
    }
}

@Composable
private fun LegacyHistoryRow(record: SalaryHistoryEntity, year: String, onAssign: (String) -> Unit) {
    var value by remember(record.id, year) { mutableStateOf(year) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("История: ${record.monthName}", modifier = Modifier.weight(1f))
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Год") }, modifier = Modifier.weight(1f), singleLine = true)
        Button(onClick = { onAssign(value) }) { Text("Сохранить") }
    }
}

@Composable
private fun LegacyMonthRow(month: MonthSalaryEntity, year: String, primary: Color, onAssign: (String) -> Unit) {
    var value by remember(month.monthIndex, year) { mutableStateOf(year) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Месяц ${month.monthIndex + 1}", modifier = Modifier.weight(1f))
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Год") }, modifier = Modifier.weight(1f), singleLine = true)
        Button(onClick = { onAssign(value) }) { Text("Сохранить") }
    }
}
