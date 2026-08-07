package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== ВЫБОР МЕСЯЦА =====

@Composable
fun MonthSelector(
    selectedMonthIndex: Int,
    onMonthSelected: (Int) -> Unit,
    selectedYear: Int = java.time.LocalDate.now().year,
    onYearSelected: (Int) -> Unit = {}
) {
    val months = MonthlyNorms.list
    var expanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val years = (2026..2035).toList()
    val primary = MaterialTheme.colorScheme.primary

    PremiumSectionCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Период расчёта",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Строка: месяц | год
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MonthYearField(
                        value = months[selectedMonthIndex].name,
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        months.forEachIndexed { index, monthData ->
                            DropdownMenuItem(
                                text = { Text(monthData.name, fontSize = 14.sp, fontWeight = if (index == selectedMonthIndex) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onMonthSelected(index)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(0.62f)) {
                    MonthYearField(
                        value = selectedYear.toString(),
                        onClick = { yearExpanded = true }
                    )
                    DropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.5f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString(), fontSize = 14.sp, fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onYearSelected(year)
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthYearField(value: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ===== РАСКРЫВАЕМАЯ СЕКЦИЯ =====

@Composable
fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val dangerColor = Color(0xFFFF5252)
    val accentColor = if (danger) dangerColor else MaterialTheme.colorScheme.primary

    PremiumSectionCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Цветной индикатор слева
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 34.dp)
                        .background(accentColor.copy(alpha = if (expanded) 1f else 0.4f), RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (danger) "⚠️" else "📌",
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (danger) dangerColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = accentColor,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 0.dp)
                            .background(Color.Transparent)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ===== ПОЛЕ ВВОДА С ИКОНКОЙ =====

@Composable
fun InputFieldWithText(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Text(icon, fontSize = 16.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    )
}
