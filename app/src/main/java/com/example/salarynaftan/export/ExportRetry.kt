package com.example.salarynaftan.export

import timber.log.Timber
import kotlinx.coroutines.delay

/**
 * Retry-механизм для операций записи файлов экспорта (п.6.1).
 *
 * При сбое записи (например, временная занятость файла антивирусом или
 * медленный кэш) выполняется до [maxAttempts] попыток с экспоненциальной
 * задержкой. Это снижает вероятность мгновенного отказа пользователю
 * из-за разовых сбоев файловой системы.
 */
object ExportRetry {

    /** Базовая задержка между попытками, мс. */
    private const val BASE_DELAY_MS = 200L

    /**
     * Выполняет [block] с повторами. Бросает последнее исключение, если
     * все попытки исчерпаны.
     *
     * @param maxAttempts число попыток (>= 1)
     * @param operationName имя операции для лога
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        operationName: String,
        block: () -> T
    ): T {
        // Защита: Thread.sleep блокирует поток. Если вызовут на Main — ANR.
        // Экспорт должен вызываться только из фонового потока.
        check(Thread.currentThread() != android.os.Looper.getMainLooper().thread) {
            "ExportRetry.withRetry нельзя вызывать на главном потоке"
        }
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastError = e
                if (attempt < maxAttempts - 1) {
                    val delay = BASE_DELAY_MS * (1L shl attempt) // 200, 400, 800…
                    Timber.w(e, "Экспорт «%s»: попытка %d/%d не удалась, повтор через %d мс",
                        operationName, attempt + 1, maxAttempts, delay)
                    delay(delay)
                }
            }
        }
        throw lastError ?: IllegalStateException("Экспорт «$operationName» не выполнен")
    }
}