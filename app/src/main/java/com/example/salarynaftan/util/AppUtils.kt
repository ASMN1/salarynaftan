package com.example.salarynaftan.util

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Общие утилиты приложения: шаринг файлов через FileProvider.
 */

/**
 * Шарит файл через FileProvider с заданным MIME-типом.
 * @return true если intent успешно запущен, false при ошибке.
 */
fun shareFile(
    context: Context,
    file: File,
    mimeType: String,
    chooserTitle: String
): Boolean {
    return try {
        // FileProvider выбрасывает IllegalArgumentException, если файла нет.
        // Проверяем существование заранее, чтобы не уронить приложение
        // при попытке поделиться несуществующим файлом (BUG-011).
        if (!file.exists()) return false

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (e: Exception) {
        false
    }
}

/** Создаёт временную директорию для экспорта и чистит старые файлы. */
fun getExportDir(context: Context): File {
    val dir = File(context.cacheDir, "exports")
    dir.mkdirs()
    // Экспорт генерирует файлы с каждым обращением (PDF/ICS/CSV/картинки),
    // которые копятся в кэше. Удаляем файлы старше суток, чтобы кэш
    // не переполнялся при частом экспорте (в т.ч. «весь год»).
    cleanOldFiles(dir, maxAgeMillis = 24L * 60 * 60 * 1000)
    return dir
}

/** Удаляет файлы в [dir] старше [maxAgeMillis]. */
private fun cleanOldFiles(dir: File, maxAgeMillis: Long) {
    val now = System.currentTimeMillis()
    dir.listFiles()?.forEach { file ->
        if (file.isFile && now - file.lastModified() > maxAgeMillis) {
            file.delete()
        }
    }
}

/**
 * Extension-обёртка над [androidx.compose.foundation.layout.RowScope.weight],
 * чтобы избежать повторяющихся `Modifier.weight(1f)` в коде.
 * Вызывается внутри `RowScope` и возвращает `Modifier` с fill-весом 1f,
 * например `Modifier.weightFill()`.
 */
fun RowScope.weightFill(): Modifier = Modifier.weight(1f)

/**
 * Адаптивный контейнер для альбомной/широкой ориентации.
 * На широких экранах (планшеты, альбомный режим) контент центрируется
 * и ограничивается по ширине — иначе многие экраны приложения
 * растягивались на всю ширину и становились неудобными.
 * На обычных портретных экранах контент занимает всю ширину как раньше.
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val isWide = widthDp >= 600 // планшеты и альбомный режим на телефоне (сложенная ширина)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .then(if (isWide) Modifier.widthIn(max = 520.dp) else Modifier.fillMaxWidth()),
            content = content
        )
    }
}