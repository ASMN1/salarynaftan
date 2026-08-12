package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== КАРТОЧКА ИСТОРИИ РАСЧЁТОВ =====

@Composable
fun HistoryCard(
    historyList: List<SalaryHistoryRecord>,
    isDarkTheme: Boolean,
    historyManager: HistoryManager,
    availableYears: List<Int> = emptyList(),
    selectedFilterYear: Int? = null
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    PremiumSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Заголовок секции
            PremiumSectionTitle(
                icon = "📚",
                title = "История выплат",
                subtitle = if (historyList.isNotEmpty()) "${historyList.size} записей" else null,
                trailing = {
                    if (historyList.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PremiumIconButton(
                                onClick = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            try {
                                                historyManager.shareCsv(context)
                                            } catch (_: Exception) {
                                                false
                                            }
                                        }
                                        // Уведомляем пользователя об ошибке экспорта вместо тихого игнора (п.6.2)
                                        // showError — Compose-состояние, менять только на Main (п.1.5).
                                        if (!ok) showError = true
                                    }
                                },
                                background = primary.copy(alpha = 0.1f),
                                contentColor = primary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            PremiumIconButton(
                                onClick = { showClearDialog = true },
                                background = DesignTokens.Danger.copy(alpha = 0.1f),
                                contentColor = DesignTokens.Danger
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Очистить",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )

            if (historyList.isEmpty()) {
                // Пустое состояние
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🗂️", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Пока нет сохранённых расчётов",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                // Фильтр по году (п.6.4)
                if (availableYears.size > 1) {
                    var showYearFilter by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Год:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Surface(
                                onClick = { showYearFilter = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = primary
                            ) {
                                Text(
                                    text = selectedFilterYear?.toString() ?: "Все",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showYearFilter,
                                onDismissRequest = { showYearFilter = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все годы", fontSize = 13.sp) },
                                    onClick = {
                                        scope.launch { historyManager.setFilterYear(null) }
                                        showYearFilter = false
                                    }
                                )
                                availableYears.forEach { y ->
                                    DropdownMenuItem(
                                        text = { Text(y.toString(), fontSize = 13.sp) },
                                        onClick = {
                                            scope.launch { historyManager.setFilterYear(y) }
                                            showYearFilter = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    historyList.forEachIndexed { index, record ->
                        // Enter animation only on first appearance
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { appeared = true }
                        AnimatedVisibility(
                            visible = appeared,
                            enter = fadeIn(tween(300, delayMillis = index * 50)) +
                                    slideInVertically(
                                        animationSpec = tween(300, delayMillis = index * 50),
                                        initialOffsetY = { it / 2 }
                                    ),
                            exit = fadeOut(tween(150)) +
                                    slideOutVertically(animationSpec = tween(150), targetOffsetY = { -it / 2 })
                        ) {
                            HistoryRecordRow(
                                record = record,
                                primary = primary,
                                onDelete = {
                                    scope.launch { historyManager.deleteRecord(record.monthIndex, record.year) }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить историю?", fontWeight = FontWeight.Bold) },
            text = { Text("Все сохранённые расчёты будут удалены без возможности восстановления.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { historyManager.deleteAll() }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.Danger,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Не удалось экспортировать", fontWeight = FontWeight.Bold) },
            text = { Text("Не удалось создать или отправить файл CSV. Попробуйте ещё раз.") },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("OK", color = primary, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun HistoryRecordRow(
    record: SalaryHistoryRecord,
    primary: Color,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.045f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${record.monthName} ${record.year}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "На руки ${MoneyFormatter.formatRub(record.cleanToPay)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary
            )
            Text(
                "Итого: ${MoneyFormatter.format1(record.totalClean)} р",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Круглая кнопка удаления
        PremiumIconButton(
            onClick = onDelete,
            background = DesignTokens.Danger.copy(alpha = 0.1f),
            contentColor = DesignTokens.Danger
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
