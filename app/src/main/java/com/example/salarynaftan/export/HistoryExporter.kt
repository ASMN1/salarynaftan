package com.example.salarynaftan.export

import com.example.salarynaftan.MoneyFormatter
import com.example.salarynaftan.util.getExportDir
import com.example.salarynaftan.util.shareFile

import android.content.Context
import com.example.salarynaftan.data.SalaryHistoryEntity
import java.io.File

/**
 * Отвечает за экспорт истории выплат в CSV и за шаринг файла.
 * Выделен из SalaryRepository, чтобы репозиторий занимался только данными,
 * а UI-задачи (построение CSV, Intent) жили в отдельном слое.
 * Ошибки пробрасываются наверх, а не глотаются — вызывающий решает,
 * как сообщить пользователю (см. HistoryCard / SalaryCalculatorScreen).
 */
class HistoryExporter(private val context: Context) {

    fun exportHistoryToCsv(records: List<SalaryHistoryEntity>): File? {
        if (records.isEmpty()) return null

        val csvContent = StringBuilder()
        csvContent.append("Месяц;Год;Итого начислено;К выплате;Аванс\n")
        records.forEach { record ->
            csvContent.append("${record.monthName};${record.year};${MoneyFormatter.format(record.totalClean)};${MoneyFormatter.format(record.cleanToPay)};${MoneyFormatter.format(record.advance)}\n")
        }

        val file = File(getExportDir(context), "history_export_${System.currentTimeMillis()}.csv")
        file.writeText(csvContent.toString())
        return file
    }

    fun shareHistoryCsv(context: Context, records: List<SalaryHistoryEntity>): Boolean {
        val file = exportHistoryToCsv(records) ?: return false
        return shareFile(context, file, mimeType = "text/csv", chooserTitle = "Экспорт истории")
    }
}
