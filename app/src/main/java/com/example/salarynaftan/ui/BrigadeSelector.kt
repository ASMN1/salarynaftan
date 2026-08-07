package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Выбор бригады для просмотра графика (№1 — выделенный компонент из ScheduleScreen).
 */
@Composable
fun BrigadeSelector(
    selectedBrigade: Int,
    onBrigadeSelected: (Int) -> Unit,
    primaryColor: Color
) {
    PremiumSectionCard {
        Column {
            PremiumSectionTitle(icon = "👥", title = "Бригада", subtitle = "Выберите смену для просмотра")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..5).forEach { num ->
                    val selected = selectedBrigade == num
                    Surface(
                        onClick = { onBrigadeSelected(num) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) primaryColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            num.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
