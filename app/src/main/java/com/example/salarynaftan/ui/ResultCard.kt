package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import timber.log.Timber

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
    pensionPercent: Int = 6,
    onPrevMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {}
) {
    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedMonthIndex = state.selectedMonthIndex
    val selectedYear = state.selectedYear
    val monthName = months.getOrNull(selectedMonthIndex)?.name ?: ""

    // Плавный count-up анимация суммы «К ВЫПЛАТЕ» при появлении результата (п.1.6).
    val animatedToPay = remember { Animatable(0f) }
    LaunchedEffect(result.cleanToPay) {
        animatedToPay.snapTo(0f)
        animatedToPay.animateTo(
            targetValue = result.cleanToPay.toFloat(),
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    PremiumSectionCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Быстрый переключатель месяца в результатах (п.3.6): позволяет
            // одним тапом перейти на соседний месяц, не возвращаясь к верху.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrevMonth, enabled = selectedMonthIndex > 0) {
                    Text("‹", fontSize = 20.sp, color = primary, fontWeight = FontWeight.Bold)
                }
                Text(
                    "$monthName $selectedYear",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onNextMonth, enabled = selectedMonthIndex < months.lastIndex) {
                    Text("›", fontSize = 20.sp, color = primary, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
                Text(MoneyFormatter.formatRub(animatedToPay.value.toDouble()), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("📈 Начисления", DesignTokens.Success)
            ResultRow("Оклад расчётный", result.okladReal)
            ResultRow("Надбавка за стаж ($stazhPercent%)", result.stazh)
            ResultRow("Доплата за вредность", result.vrednost)
            ResultRow("Ночные часы (${result.nightHours.toInt()} ч)", result.nochPay)
            if (result.prazdn > 0) ResultRow("Праздничные часы", result.prazdn)
            ResultRow("Премия (за прошлый мес.)", result.prem)
            if (parseNonNegative(state.zaOtsutstvuushego) > 0) ResultRow("За отсутств. сотрудника", parseNonNegative(state.zaOtsutstvuushego))
            if (parseNonNegative(state.kvartalka) > 0) ResultRow("Квартальная премия", parseNonNegative(state.kvartalka))
            if (result.profMasterstvo > 0) ResultRow("Профмастерство", result.profMasterstvo)
            if (result.intensyvnost > 0) ResultRow("Интенсивность труда", result.intensyvnost)
            parseExtraItems(state.inyeVyplatyInput).forEach { it -> if (it.amount > 0) ResultRow("Иные выплаты: ${if (it.name.isBlank()) "позиция" else it.name}", it.amount) }
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

            ResultSectionLabel("🧾 Налоговая база", DesignTokens.TaxBase)
            ResultRow("Общая сумма", result.dirty)
            ResultRowGray("Налоговый вычет на детей (${displayInt(state.childrenCountInput)} дет.)", -result.childrenDeduction)
            if (result.mmDeti > 0) ResultRowGray("МП на детей (не облагается)", -result.mmDeti)
            ResultRowGray("Облагаемый доход", result.podohodnyBase)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("🔻 Удержания", DesignTokens.Danger)
            ResultRow("ФСЗН (1%)", result.fszn)
            ResultRow("Профсоюз (1%)", result.prof)
            ResultRow("Подоходный налог (13%)", result.podohodny)
            if (parseNonNegative(state.gazetaInput) > 0) ResultRow("Газета", parseNonNegative(state.gazetaInput))
            if (parseNonNegative(state.pozhertvovanjaInput) > 0) ResultRow("Пожертвования", parseNonNegative(state.pozhertvovanjaInput))
            if (parseNonNegative(state.subbotnikInput) > 0) ResultRow("Субботник", parseNonNegative(state.subbotnikInput))
            if (parseNonNegative(state.stravitaInput) > 0) ResultRow("Стравита", parseNonNegative(state.stravitaInput))
            parseExtraItems(state.inyeUderzhanijaInput).forEach { it -> if (it.amount > 0) ResultRow("Иные удержания: ${if (it.name.isBlank()) "позиция" else it.name}", it.amount) }

            val totalUderzhano = result.fszn + result.prof + result.podohodny +
                    parseNonNegative(state.gazetaInput) + parseNonNegative(state.pozhertvovanjaInput) +
                    parseNonNegative(state.subbotnikInput) + parseNonNegative(state.stravitaInput) +
                    parseNonNegative(state.inyeUderzhanijaInput)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ВСЕГО УДЕРЖАНО:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesignTokens.Danger)
                Text(MoneyFormatter.formatRub(totalUderzhano), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesignTokens.Danger)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ResultSectionLabel("💰 Итоги", DesignTokens.Success)
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
                                // Поделиться PDF через системный chooser (соцсети, мессенджеры и т.д.),
                                // а не только открыть для просмотра.
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Поделиться расчётом (PDF)"))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Ошибка экспорта расчёта в PDF")
                            withContext(Dispatchers.Main) {
                                AppNotifier.showError("Не удалось экспортировать PDF")
                            }
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
