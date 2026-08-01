package com.example.salarynaftan

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionName = getAppVersion(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ℹ️ О приложении",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.DarkGray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📅", fontSize = 36.sp)
                    Column {
                        Text(
                            "Календарь Смен ОАО \"Нафтан\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            "Версия $versionName",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                Text(
                    "Приложение для расчёта зарплаты и отображения графика смен для работников ОАО \"Нафтан\".",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "👨‍💻 Разработчик: Артем Манчинский",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "📧 Email: manchinskyas@yandex.ru",
                    fontSize = 11.sp,
                    color = Color(0xFF00E676),
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:manchinskyas@yandex.ru")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Нет приложения для отправки почты", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Text(
                    "🔗 Политика конфиденциальности",
                    fontSize = 11.sp,
                    color = Color(0xFF00E676),
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/salarynaftan/privacy"))
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Нет браузера для открытия ссылки", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))

                Button(
                    onClick = { shareApp(context) },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("📤 Поделиться приложением", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.DarkGray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "📚 Используемые библиотеки",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
                Text("• Jetpack Compose", fontSize = 11.sp, color = Color.Gray)
                Text("• Material Design 3", fontSize = 11.sp, color = Color.Gray)
                Text("• AndroidX Core", fontSize = 11.sp, color = Color.Gray)
                Text("• Kotlin Coroutines", fontSize = 11.sp, color = Color.Gray)
            }
        }
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
        Toast.makeText(context, "Нет приложений для шаринга", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}