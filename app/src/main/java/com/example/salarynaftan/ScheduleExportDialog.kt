package com.example.salarynaftan

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

@Composable
fun ScheduleExportDialog(
    month: YearMonth,
    brigade: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportYear by remember { mutableStateOf(false) }
    var pdfFormat by remember { mutableStateOf(true) }

    // Раньше PDF/картинка создавались прямо в onClick на главном потоке —
    // для "весь год" это заметная по времени операция и реальный риск
    // подвесить интерфейс ("Приложение не отвечает"). Теперь это уходит в
    // фон; isExporting не даёт запустить второй экспорт поверх первого и
    // блокирует закрытие диалога, пока файл не готов (иначе корутина,
    // привязанная к rememberCoroutineScope, оборвётся вместе с диалогом).
    var isExporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isExporting) onDismiss()
        },
        title = {
            Text("📤 Экспорт графика")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                Text("Бригада: $brigade")
                Text("Месяц: ${month.monthValue}.${month.year}")

                Spacer(modifier = Modifier.height(10.dp))
                Text("Что экспортировать:")

                Row {
                    RadioButton(
                        selected = !exportYear,
                        onClick = { exportYear = false },
                        enabled = !isExporting
                    )
                    Text("Только месяц")
                }

                Row {
                    RadioButton(
                        selected = exportYear,
                        onClick = { exportYear = true },
                        enabled = !isExporting
                    )
                    Text("Весь год")
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Формат:")

                Row {
                    RadioButton(
                        selected = pdfFormat,
                        onClick = { pdfFormat = true },
                        enabled = !isExporting
                    )
                    Text("📄 PDF")
                }

                Row {
                    RadioButton(
                        selected = !pdfFormat,
                        onClick = { pdfFormat = false },
                        enabled = !isExporting
                    )
                    Text("🖼 Картинка")
                }

                if (isExporting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Создаём файл, подождите...", fontSize = 12.sp)
                    }
                }

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isExporting,
                onClick = {
                    errorMessage = null
                    isExporting = true

                    scope.launch(Dispatchers.IO) {
                        try {
                            val file: File = if (pdfFormat) {
                                if (exportYear) {
                                    SchedulePdfExporter.createYearPdf(
                                        context = context,
                                        brigade = brigade,
                                        year = month.year
                                    )
                                } else {
                                    SchedulePdfExporter.createMonthPdf(
                                        context = context,
                                        brigade = brigade,
                                        month = month
                                    )
                                }
                            } else {
                                if (exportYear) {
                                    ScheduleImageExporter.createYearImage(
                                        context = context,
                                        brigade = brigade,
                                        year = month.year
                                    )
                                } else {
                                    ScheduleImageExporter.createMonthImage(
                                        context = context,
                                        brigade = brigade,
                                        month = month
                                    )
                                }
                            }

                            withContext(Dispatchers.Main) {
                                isExporting = false
                                shareFile(context, file)
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isExporting = false
                                errorMessage = "Не удалось создать файл: ${e.message ?: "не хватает памяти или места на диске"}"
                            }
                        }
                    }
                }
            ) {
                Text(if (isExporting) "Создаём..." else "Создать")
            }
        }
    )
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val mime = when (file.extension.lowercase()) {
            "png", "jpg", "jpeg" -> "image/*"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Поделиться графиком"))
    } catch (e: Exception) {
        e.printStackTrace()
        // Файл создан успешно, но поделиться не вышло (например, на
        // устройстве нет ни одного приложения, принимающего ACTION_SEND) —
        // это не повод ронять приложение.
    }
}
