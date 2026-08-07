package com.example.salarynaftan

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.salarynaftan.ui.AboutScreen
import com.example.salarynaftan.ui.AlarmsTabScreen
import com.example.salarynaftan.ui.SalaryCalculatorScreen
import com.example.salarynaftan.ui.ScheduleScreen
import com.example.salarynaftan.ui.SettingsScreen
import com.example.salarynaftan.util.AdaptiveContent
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не важен */ }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display ?: return
            val modes = display.supportedModes
            val highestRate = modes.maxByOrNull { it.refreshRate } ?: return
            if (highestRate.refreshRate > display.mode.refreshRate) {
                val params = window.attributes
                params.preferredDisplayModeId = highestRate.modeId
                window.attributes = params
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        val prefs = getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("fsi_prompted", false)) return

        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.canUseFullScreenIntent()) {
            prefs.edit().putBoolean("fsi_prompted", true).apply()
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                try { startActivity(fallback) } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRate()
        ensureNotificationPermission()
        ensureFullScreenIntentPermission()
        setContent {
            val settings = koinInject<SettingsManager>()
            val context = LocalContext.current

            // Состояния цветов и темы (изменяемые без recreate)
            var isDarkTheme by remember { mutableStateOf(settings.isDarkTheme()) }

            var useDynamicColors by remember { mutableStateOf(settings.getUseDynamicColors()) }

            var targetPrimary by remember { mutableStateOf(settings.getPrimaryColor()) }
            var targetBackground by remember { mutableStateOf(settings.getBackgroundColor()) }
            var targetSurface by remember { mutableStateOf(settings.getSurfaceColor()) }

            // Плавная анимация цветов
            val animatedPrimary by animateColorAsState(
                targetValue = targetPrimary,
                animationSpec = tween(durationMillis = 500),
                label = "primaryColor"
            )
            val animatedBackground by animateColorAsState(
                targetValue = targetBackground,
                animationSpec = tween(durationMillis = 500),
                label = "bgColor"
            )
            val animatedSurface by animateColorAsState(
                targetValue = targetSurface,
                animationSpec = tween(durationMillis = 500),
                label = "surfaceColor"
            )

            // Масштаб интерфейса: масштабирует все dp/sp равномерно во всех вкладках.
            var uiScale by remember { mutableStateOf(settings.getUiScale()) }
            val baseDensity = LocalDensity.current
            val scaledDensity = Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale
            )

            val colorScheme = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDarkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else if (isDarkTheme) {                darkColorScheme(
                    primary = animatedPrimary,
                    background = animatedBackground,
                    surface = animatedSurface,
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            } else {
                lightColorScheme(
                    primary = animatedPrimary,
                    background = animatedBackground,
                    surface = animatedSurface,
                    onPrimary = Color.Black,
                    onBackground = Color.Black,
                    onSurface = Color.Black
                )
            }

            fun updateTheme(isDark: Boolean) {
                isDarkTheme = isDark
                useDynamicColors = settings.getUseDynamicColors()
                settings.saveTheme(isDark)
                val newBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
                val newSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
                targetBackground = newBg
                targetSurface = newSurface
                settings.saveBackgroundColor(newBg)
                settings.saveSurfaceColor(newSurface)
            }

            fun updateColors(newPrimary: Color, newBg: Color, newSurface: Color) {
                targetPrimary = newPrimary
                targetBackground = newBg
                targetSurface = newSurface
                settings.savePrimaryColor(newPrimary)
                settings.saveBackgroundColor(newBg)
                settings.saveSurfaceColor(newSurface)
            }

            // Масштаб интерфейса применяется ко ВСЕМ вкладкам через LocalDensity.
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MaterialTheme(colorScheme = colorScheme) {
                    MainAppScreen(
                        isDarkTheme = isDarkTheme,
                        onThemeChange = { updateTheme(it) },
                        primaryColor = animatedPrimary,
                        backgroundColor = animatedBackground,
                        surfaceColor = animatedSurface,
                        onColorsChange = { newPrimary, newBg, newSurface ->
                            updateColors(newPrimary, newBg, newSurface)
                        },
                        uiScale = uiScale,
                        onUiScaleChange = {
                            uiScale = it
                            settings.saveUiScale(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    primaryColor: Color,
    backgroundColor: Color,
    surfaceColor: Color,
    onColorsChange: (Color, Color, Color) -> Unit,
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var selectedTab by rememberSaveable {
        mutableIntStateOf(activity?.intent?.getIntExtra("selected_tab", 0) ?: 0)
    }

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
                    icon = { Text("📅", fontSize = 18.sp) },
                    label = { Text("График", fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("💰", fontSize = 18.sp) },
                    label = { Text("Зарплата", fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    label = { Text("Настройки", fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("⏰", fontSize = 20.sp) },
                    label = { Text("Будильники", fontSize = 10.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Text("ℹ️", fontSize = 18.sp) },
                    label = { Text("О приложении", fontSize = 8.sp, maxLines = 1) },
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
                // Адаптивный контейнер: на широких/альбомных экранах
                // центрирует контент, ограничивая ширину (удобство на планшетах).
                AdaptiveContent {
                    when (tab) {
                        0 -> ScheduleScreen(
                            isDarkTheme = isDarkTheme,
                            onThemeChange = onThemeChange,
                            primaryColor = primaryColor
                        )
                        1 -> SalaryCalculatorScreen(
                            isDarkTheme = isDarkTheme
                        )
                        2 -> SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onThemeChange = onThemeChange,
                            onColorsChange = onColorsChange,
                            currentPrimaryColor = primaryColor,
                            currentBackgroundColor = backgroundColor,
                            currentSurfaceColor = surfaceColor,
                            uiScale = uiScale,
                            onUiScaleChange = onUiScaleChange
                        )
                        3 -> AlarmsTabScreen()  // <-- ИСПРАВЛЕНО: теперь без параметров
                        4 -> AboutScreen()
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