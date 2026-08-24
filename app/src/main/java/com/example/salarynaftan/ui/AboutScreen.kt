package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionName = getAppVersion(context)
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        PremiumHeader(
            title = "О приложении",
            subtitle = "Календарь смен · расчёты · будильники"
        )

        // ===== КАРТОЧКА ПРИЛОЖЕНИЯ =====
        PremiumSectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Логотип-плашка
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "📅",
                            fontSize = 32.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Календарь Смен",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary
                        )
                        Text(
                            text = "ОАО «Нафтан»",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PremiumChip(
                            text = "Версия $versionName",
                            background = primary.copy(alpha = 0.12f),
                            contentColor = primary
                        )
                    }
                }

                PremiumDivider()

                Text(
                    text = "Приложение для расчёта зарплаты, отображения графика смен и управления будильниками для работников ОАО «Нафтан».",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                // Строка разработчика
                ContactRow(
                    icon = "👨‍💻",
                    title = "Разработчик",
                    subtitle = "Артем Манчинский"
                )
                // Строка email
                ClickableContactRow(
                    icon = "📧",
                    title = "Email",
                    subtitle = "manchinskyas@yandex.ru",
                    onClick = {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:manchinskyas@yandex.ru")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            if (emailIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(Intent.createChooser(emailIntent, "Отправить письмо"))
                            } else {
                                AppNotifier.showError("Нет приложения для отправки почты")
                            }
                        } catch (e: Exception) {
                            AppNotifier.showError("Не удалось открыть почту")
                        }
                    }
                )
                // Строка политики
                ClickableContactRow(
                    icon = "🔗",
                    title = "Политика конфиденциальности",
                    subtitle = "sites.google.com/view/salarynaftan",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/salarynaftan/privacy")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                AppNotifier.showError("Нет браузера для открытия ссылки")
                            }
                        } catch (e: Exception) {
                            AppNotifier.showError("Не удалось открыть ссылку")
                        }
                    }
                )
            }
        }

        // ===== КНОПКА ПОДЕЛИТЬСЯ =====
        PremiumSectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { shareApp(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("📤 Поделиться приложением", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        // ===== ИСПОЛЬЗУЕМЫЕ БИБЛИОТЕКИ =====
        PremiumSectionCard {
            PremiumSectionTitle(icon = "📚", title = "Используемые библиотеки")
            PremiumDivider()
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Jetpack Compose" to "🖌️",
                    "Material Design 3" to "🎨",
                    "AndroidX Core" to "📦",
                    "Kotlin Coroutines" to "🌀"
                ).forEach { (name, icon) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ContactRow(icon: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ContactIcon(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(subtitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ClickableContactRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactIcon(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    }
}

@Composable
private fun ContactIcon(icon: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Text(
            text = icon,
            fontSize = 16.sp,
            modifier = Modifier.padding(10.dp)
        )
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "1.0"
    } catch (e: PackageManager.NameNotFoundException) {
        "1.0"
    }
}

fun shareApp(context: Context) {
    val shareText = """
        Попробуй приложение "Календарь Смен ОАО \"Нафтан\""!
        Оно помогает рассчитывать зарплату и следить за графиком смен.
        
        Скачать можно здесь: https://sites.google.com/view/salarynaftan/download
    """.trimIndent()

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }

    try {
        val chooserIntent = Intent.createChooser(shareIntent, "Поделиться приложением")
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        AppNotifier.showError("Нет приложений для шаринга")
        Timber.e(e, "Не удалось поделиться приложением")
    }
}
