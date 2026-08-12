package com.example.salarynaftan.ui
import com.example.salarynaftan.*
import com.example.salarynaftan.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.salarynaftan.export.ScheduleIcsExporter
import com.example.salarynaftan.export.ScheduleImageExporter
import com.example.salarynaftan.export.SchedulePdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

@Composable
fun ScheduleExportDialog(
    month: YearMonth,
    brigade: Int,
    scheduleType: ScheduleType,
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
            Text(stringResource(R.string.export_schedule_title))
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

                Spacer(modifier = Modifier.height(10.dp))
                Text("Другое:")
                Row {
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val icsFile = ScheduleIcsExporter.createIcsFile(
                                        context, month, brigade, scheduleType
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (icsFile != null) {
                                            shareFile(context, icsFile)
                                            onDismiss()
                                        } else {
                                            errorMessage = "Нет смен в этом месяце для экспорта"
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = "Не удалось создать ICS-файл"
                                    }
                                }
                            }
                        }
                    ) {
                        Text("📅 В календарь (.ics)")
                    }
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
                                        year = month.year,
                                        scheduleType = scheduleType
                                    )
                                } else {
                                    SchedulePdfExporter.createMonthPdf(
                                        context = context,
                                        brigade = brigade,
                                        month = month,
                                        scheduleType = scheduleType
                                    )
                                }
                            } else {
                                if (exportYear) {
                                    ScheduleImageExporter.createYearImage(
                                        context = context,
                                        brigade = brigade,
                                        year = month.year,
                                        scheduleType = scheduleType
                                    )
                                } else {
                                    ScheduleImageExporter.createMonthImage(
                                        context = context,
                                        brigade = brigade,
                                        month = month,
                                        scheduleType = scheduleType
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

private fun shareFile(context: android.content.Context, file: java.io.File) {
    val mime = when (file.extension.lowercase()) {
        "png", "jpg", "jpeg" -> "image/*"
        "pdf" -> "application/pdf"
        "ics" -> "text/calendar"
        else -> "*/*"
    }
    com.example.salarynaftan.util.shareFile(context, file, mime, "Поделиться графиком")
}
