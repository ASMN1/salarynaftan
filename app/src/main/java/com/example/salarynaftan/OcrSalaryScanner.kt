package com.example.salarynaftan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * OCR-сканер ведомости зарплаты.
 * Распознаёт текст из фото/изображения с помощью ML Kit и
 * пытается найти числовые значения для полей расчёта.
 */
class OcrSalaryScanner : java.io.Closeable {

    data class OcrResult(
        val normHours: String? = null,
        val factHours: String? = null,
        val nightShifts: String? = null,
        val s4Shifts: String? = null,
        val advanceShifts: String? = null,
        val prazdnHours: String? = null,
        val rawText: String = "",
        val recognizedNumbers: List<Pair<String, String>> = emptyList() // label -> value
    )

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Распознаёт текст из Uri изображения.
     */
    suspend fun recognizeText(context: Context, imageUri: Uri): String {
        return suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        cont.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        cont.resume("ОШИБКА: ${e.localizedMessage}")
                    }
            } catch (e: Exception) {
                cont.resume("ОШИБКА: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Распознаёт текст из Bitmap.
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        cont.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        cont.resume("ОШИБКА: ${e.localizedMessage}")
                    }
            } catch (e: Exception) {
                cont.resume("ОШИБКА: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Парсит распознанный текст и извлекает данные для полей зарплаты.
     * Ищет паттерны вроде "Норма: 168", "Факт: 152", "Ночные: 8" и т.д.
     */
    override fun close() {
        recognizer.close()
    }

    fun parseSalaryFields(rawText: String): OcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val foundNumbers = mutableListOf<Pair<String, String>>()

        var normHours: String? = null
        var factHours: String? = null
        var nightShifts: String? = null
        var s4Shifts: String? = null
        var advanceShifts: String? = null
        var prazdnHours: String? = null

        // Регулярки для поиска числовых значений
        val numberPattern = Regex("""(\d+[.,]?\d*)""")

        for (line in lines) {
            val lower = line.lowercase()

            // Норма часов
            if (normHours == null && (lower.contains("норм") || lower.contains("должно") || lower.contains("план"))) {
                numberPattern.find(line)?.let {
                    normHours = it.value.replace(",", ".")
                    foundNumbers.add("Норма часов" to normHours!!)
                }
            }

            // Факт часов
            if (factHours == null && (lower.contains("факт") || lower.contains("отработ") || lower.contains("выполнен"))) {
                numberPattern.find(line)?.let {
                    factHours = it.value.replace(",", ".")
                    foundNumbers.add("Факт часов" to factHours!!)
                }
            }

            // Ночные смены/часы
            if (nightShifts == null && (lower.contains("ночн") || lower.contains("ночь"))) {
                numberPattern.find(line)?.let {
                    nightShifts = it.value.replace(",", ".")
                    foundNumbers.add("Ночные смены" to nightShifts!!)
                }
            }

            // Смены "с 4"
            if (s4Shifts == null && (lower.contains("с 4") || lower.contains("с4") || lower.contains("дневн"))) {
                numberPattern.find(line)?.let {
                    s4Shifts = it.value.replace(",", ".")
                    foundNumbers.add("Смены \"с 4\"" to s4Shifts!!)
                }
            }

            // Аванс
            if (advanceShifts == null && (lower.contains("аванс") || lower.contains("предоплат"))) {
                numberPattern.find(line)?.let {
                    advanceShifts = it.value.replace(",", ".")
                    foundNumbers.add("Смены аванса" to advanceShifts!!)
                }
            }

            // Праздничные
            if (prazdnHours == null && (lower.contains("праздн") || lower.contains("выходн") || lower.contains("нерабоч"))) {
                numberPattern.find(line)?.let {
                    prazdnHours = it.value.replace(",", ".")
                    foundNumbers.add("Праздничные часы" to prazdnHours!!)
                }
            }
        }

        // Если ничего не нашли по ключевым словам, пробуем извлечь все числа из текста
        if (foundNumbers.isEmpty()) {
            val allNumbers = numberPattern.findAll(rawText)
                .map { it.value.replace(",", ".") }
                .filter { it.toDoubleOrNull() != null && it.toDouble() > 0 }
                .take(10)
                .toList()
            allNumbers.forEachIndexed { index, num ->
                foundNumbers.add("Число #${index + 1}" to num)
            }
        }

        return OcrResult(
            normHours = normHours,
            factHours = factHours,
            nightShifts = nightShifts,
            s4Shifts = s4Shifts,
            advanceShifts = advanceShifts,
            prazdnHours = prazdnHours,
            rawText = rawText,
            recognizedNumbers = foundNumbers
        )
    }
}
