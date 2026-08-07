package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Вью-превью экранов — позволяет смотреть компоненты в Android Studio
 * Design-вкладке без запуска приложения на устройстве/эмуляторе.
 */

@Preview(name = "Бригада — выбор", showBackground = true, widthDp = 360, heightDp = 140)
@Composable
fun PreviewBrigadeSelector() {
    MaterialTheme {
        PremiumSectionCard {
            PremiumSectionTitle(icon = "👥", title = "Бригада", subtitle = "Выберите смену")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                listOf(1, 2, 3, 4, 5).forEachIndexed { i, num ->
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        contentColor = if (i == 0) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            num.toString(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Заголовок экрана", showBackground = true, widthDp = 360, heightDp = 100)
@Composable
fun PreviewHeader() {
    MaterialTheme {
        PremiumHeader(title = "График смен", subtitle = "Бригада · календарь · отпуска")
    }
}

@Preview(name = "Shimmer-загрузка", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun PreviewShimmer() {
    MaterialTheme {
        ShimmerList(rows = 4)
    }
}
