package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.salarynaftan.export.SalaryPdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== КАРТОЧКА РЕЗУЛЬТАТОВ РАСЧЁТА =====

// Вместо десятков позиционных аргументов карточка принимает готовые
// структуры SalaryUiState и CalculationResultWithError — это устраняет
// дублирование полей и риск перепутать параметры (God-object → данные).
@Composable
fun ResultCard(
    state: SalaryUiState,
    result: CalculationResultWithError,
    months: List<MonthData>,
    historyManager: HistoryManager,
    stazhPercent: Int = 25,
    premiumPercent: Int = 45,
    pensionPercent: Int = 6
) {
    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedMonthIndex = state.selectedMonthIndex
    val selectedYear = state.selectedYear
    val monthName = months.getOrNull(selectedMonthIndex)?.name ?: ""

    PremiumSectionCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Итоговая строка «К ВЫПЛАТЕ»
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primary, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("К ВЫПЛАТЕ:", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(MoneyFormatter.formatRub(result.cleanToPay), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("📈 Начисления", Color(0xFF00E676))
            ResultRow("Оклад расчётный", result.okladReal)
            ResultRow("Надбавка за стаж ($stazhPercent%)", result.stazh)
            ResultRow("Доплата за вредность", result.vrednost)
            ResultRow("Ночные часы (${result.nightHours.toInt()} ч)", result.nochPay)
            if (result.prazdn > 0) ResultRow("Праздничные часы", result.prazdn)
            ResultRow("Премия (за прошлый мес.)", result.prem)
            ResultRow("За отсутств. сотрудника", parseNonNegative(state.zaOtsutstvuushego))
            ResultRow("Квартальная премия", parseNonNegative(state.kvartalka))
            if (result.mmDeti > 0) ResultRow("МП на детей до 3л (${displayInt(state.mmDetiCountInput)} баз.вел.)", result.mmDeti)
            ResultRow("ППС ($pensionPercent%)", result.pension)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ВСЕГО НАЧИСЛЕНО:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(MoneyFormatter.formatRub(result.dirty), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("🧾 Налоговая база", Color(0xFFFFA726))
            ResultRow("Общая сумма", result.dirty)
            ResultRowGray("Налоговый вычет на детей (${displayInt(state.childrenCountInput)} дет.)", -result.childrenDeduction)
            if (result.mmDeti > 0) ResultRowGray("МП на детей (не облагается)", -result.mmDeti)
            ResultRowGray("Облагаемый доход", result.podohodnyBase)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("🔻 Удержания", Color(0xFFFF5252))
            ResultRow("ФСЗН (1%)", result.fszn)
            ResultRow("Профсоюз (1%)", result.prof)
            ResultRow("Подоходный налог (13%)", result.podohodny)
            ResultRow("Газета", parseNonNegative(state.gazetaInput))
            ResultRow("Пожертвования", parseNonNegative(state.pozhertvovanjaInput))
            ResultRow("Субботник", parseNonNegative(state.subbotnikInput))
            ResultRow("Стравита", parseNonNegative(state.stravitaInput))

            val totalUderzhano = result.fszn + result.prof + result.podohodny +
                    parseNonNegative(state.gazetaInput) + parseNonNegative(state.pozhertvovanjaInput) +
                    parseNonNegative(state.subbotnikInput) + parseNonNegative(state.stravitaInput)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ВСЕГО УДЕРЖАНО:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFF5252))
                Text(MoneyFormatter.formatRub(totalUderzhano), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFF5252))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("💰 Итоги", Color(0xFF00E676))
            ResultRow("Всего чистыми за месяц", result.totalClean)
            ResultRow("Выплачено в аванс", -result.avans)

            // Кнопки действий
            Button(
                onClick = {
                    scope.launch {
                        historyManager.saveRecord(
                            selectedMonthIndex,
                            selectedYear,
                            monthName,
                            result.totalClean,
                            result.cleanToPay,
                            result.avans
                        )
                    }
                    },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("💾 Сохранить в историю", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            // Кнопка поделиться расчётом
            OutlinedButton(
                onClick = {
                    val shareText = buildString {
                        appendLine("📊 Расчёт зарплаты — $monthName $selectedYear")
                        appendLine()
                        appendLine("💰 К выплате: ${MoneyFormatter.formatRub(result.cleanToPay)}")
                        appendLine()
                        appendLine("Начисления:")
                        appendLine("  Оклад расчётный: ${MoneyFormatter.format(result.okladReal)}")
                        appendLine("  Стаж: ${MoneyFormatter.format(result.stazh)}")
                        appendLine("  Вредность: ${MoneyFormatter.format(result.vrednost)}")
                        appendLine("  Ночные (${result.nightHours.toInt()} ч): ${MoneyFormatter.format(result.nochPay)}")
                        appendLine("  Премия: ${MoneyFormatter.format(result.prem)}")
                        appendLine()
                        appendLine("Удержания:")
                        appendLine("  Подоходный: ${MoneyFormatter.format(result.podohodny)}")
                        appendLine()
                        appendLine("Всего чистыми: ${MoneyFormatter.formatRub(result.totalClean)}")
                        appendLine("Аванс: ${MoneyFormatter.formatRub(result.avans)}")
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться расчётом"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("📤 Поделиться расчётом", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            // Кнопка экспорта расчёта в PDF
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val file = SalaryPdfExporter.createPdf(
                                context = context,
                                monthName = "$monthName $selectedYear",
                                state = state,
                                result = result,
                                stazhPercent = stazhPercent,
                                premiumPercent = premiumPercent,
                                pensionPercent = pensionPercent
                            )
                            withContext(Dispatchers.Main) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(viewIntent, "Открыть PDF"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("📄 Экспорт расчёта в PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ResultSectionLabel(text: String, color: Color) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
}

// ===== СТРОКИ РЕЗУЛЬТАТОВ =====

@Composable
fun ResultRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Text(MoneyFormatter.formatRub(value), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ResultRowGray(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(MoneyFormatter.formatRub(value), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
