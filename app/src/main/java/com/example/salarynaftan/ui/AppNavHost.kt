package com.example.salarynaftan.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarynaftan.AppNotificationHost
import com.example.salarynaftan.PermissionManager
import com.example.salarynaftan.R
import com.example.salarynaftan.util.AdaptiveContent

/**
 * Навигация приложения (п.3.1): вынесена из MainActivity в отдельный composable.
 *
 * Содержит нижнюю панель вкладок и переключение экранов с анимацией.
 * Состояние каждой вкладки сохраняется через rememberSaveableStateHolder.
 */
@Composable
fun AppNavHost(
    theme: AppThemeScope,
    permissionManager: PermissionManager
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var selectedTab by rememberSaveable {
        mutableIntStateOf(activity?.intent?.getIntExtra("selected_tab", 0) ?: 0)
    }

    // Сохраняет состояние каждой вкладки (выбранный месяц, раскрытые секции,
    // введённые поля) при переключении и повороте экрана (п.5.1 анализа).
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppNotificationHost() },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Text(
                            "📅",
                            fontSize = 18.sp,
                            modifier = Modifier.semantics { contentDescription = "График смен" }
                        )
                    },
                    label = { Text(stringResource(R.string.tab_schedule), fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Text(
                            "💰",
                            fontSize = 18.sp,
                            modifier = Modifier.semantics { contentDescription = "Расчёт зарплаты" }
                        )
                    },
                    label = { Text(stringResource(R.string.tab_salary), fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Text(
                            "⚙️",
                            fontSize = 18.sp,
                            modifier = Modifier.semantics { contentDescription = "Настройки" }
                        )
                    },
                    label = { Text(stringResource(R.string.tab_settings), fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("⏰", fontSize = 20.sp) },
                    label = { Text(stringResource(R.string.tab_alarms), fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Text("ℹ️", fontSize = 18.sp) },
                    label = { Text(stringResource(R.string.tab_about), fontSize = 8.sp, maxLines = 1) },
                    colors = navItemColors()
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .clipToBounds()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                                initialOffsetX = { if (targetState > initialState) it else -it }
                            ) togetherWith
                            fadeOut(animationSpec = tween(150)) +
                            slideOutHorizontally(
                                animationSpec = tween(150),
                                targetOffsetX = { if (targetState > initialState) -it else it }
                            )
                },
                label = "tab_animation"
            ) { tab ->
                // Сохраняем состояние вкладки (месяц, раскрытые секции, ввод),
                // чтобы табы не сбрасывались при переключении/повороте.
                saveableStateHolder.SaveableStateProvider(key = tab) {
                    AdaptiveContent {
                        when (tab) {
                            0 -> ScheduleScreen(
                                isDarkTheme = theme.isDarkTheme,
                                onThemeChange = theme.onThemeChange,
                                primaryColor = theme.primaryColor,
                                permissionManager = permissionManager
                            )
                            1 -> SalaryCalculatorScreen(
                                isDarkTheme = theme.isDarkTheme
                            )
                            2 -> SettingsScreen(
                                isDarkTheme = theme.isDarkTheme,
                                onThemeChange = theme.onThemeChange,
                                onColorsChange = theme.onColorsChange,
                                currentPrimaryColor = theme.primaryColor,
                                currentBackgroundColor = theme.backgroundColor,
                                currentSurfaceColor = theme.surfaceColor,
                                uiScale = theme.uiScale,
                                onUiScaleChange = theme.onUiScaleChange,
                                useOled = theme.useOled,
                                onOledChange = theme.onOledChange
                            )
                            3 -> AlarmsTabScreen()
                            4 -> AboutScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun navItemColors(): NavigationBarItemColors {
    return NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}