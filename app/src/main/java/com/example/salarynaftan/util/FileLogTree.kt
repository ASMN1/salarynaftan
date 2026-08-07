package com.example.salarynaftan.util

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Файловый Timber.Tree для продакшн-диагностики.
 *
 * Пишет логи в app-private каталог (filesDir/logs/salarynaftan.log), который
 * не требует разрешений и накапливается, давая видимость крашей и ошибок,
 * которые иначе «тонут» в logcat в релизной сборке. Каждая строка:
 *   <время> <уровень> <тег> <сообщение>
 *
 * Ограничения (защита от бесконтрольного роста):
 *  - файл ротируется по лимиту [MAX_FILE_BYTES] (обрезается до хвоста);
 *  - список файлов ограничивается [MAX_LOG_FILES].
 * Все операции записи — короткие и синхронные (не критичны по скорости).
 */
class FileLogTree(context: Context) : Timber.DebugTree() {

    private val logDir = File(context.filesDir, "logs").apply { mkdirs() }
    private val logFile = File(logDir, "salarynaftan.log")
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            trimOldLogs()
            val line = buildString {
                append(timestampFormat.format(Date()))
                append(' ')
                append(priorityToLetter(priority))
                append(" [").append(tag ?: "?").append("] ")
                append(message)
                if (t != null) {
                    append('\n')
                    append(Log.getStackTraceString(t))
                }
                append('\n')
            }
            logFile.appendText(line)
            if (logFile.length() > MAX_FILE_BYTES) {
                rotate()
            }
        } catch (_: Exception) {
            // Логи не должны ронять приложение; при сбое записи молчим.
        }
    }

    private fun priorityToLetter(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    /** Обрезает слишком длинную строку стека, чтобы не сломать структуру файла. */
    private fun trimOldLogs() {
        // Удаляем старые ротированные файлы сверх лимита.
        val files = logDir.listFiles { f -> f.name.startsWith("salarynaftan") && f.isFile }
            ?: return
        if (files.size > MAX_LOG_FILES) {
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_LOG_FILES)
                .forEach { it.delete() }
        }
    }

    private fun rotate() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rotated = File(logDir, "salarynaftan_$stamp.log")
        if (logFile.exists() && logFile.renameTo(rotated)) {
            logFile.createNewFile()
        }
    }

    companion object {
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024 // 2 МБ
        private const val MAX_LOG_FILES = 5
    }
}
