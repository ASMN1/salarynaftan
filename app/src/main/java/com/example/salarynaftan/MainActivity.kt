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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

            // Получаем сохранённые цвета
            val primaryColor = settings.getPrimaryColor()
            val backgroundColor = settings.getBackgroundColor()
            val surfaceColor = settings.getSurfaceColor()

            var isDarkTheme by remember {
                mutableStateOf(settings.isDarkTheme())
            }

            val colorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = primaryColor,
                    background = backgroundColor,
                    surface = surfaceColor,
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            } else {
                lightColorScheme(
                    primary = primaryColor,
                    background = backgroundColor,
                    surface = surfaceColor,
                    onPrimary = Color.Black,
                    onBackground = Color.Black,
                    onSurface = Color.Black
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                MainAppScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = { isDark ->
                        isDarkTheme = isDark
                        settings.saveTheme(isDark)
                        // При смене темы сбрасываем фон и карточки на стандартные для этой темы
                        val newBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
                        val newSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
                        settings.saveBackgroundColor(newBg)
                        settings.saveSurfaceColor(newSurface)
                        // Перезапускаем активность, чтобы применить изменения
                        recreate()
                    },
                    primaryColor = primaryColor,
                    backgroundColor = backgroundColor,
                    surfaceColor = surfaceColor,
                    onColorsChange = { newPrimary, newBg, newSurface ->
                        settings.savePrimaryColor(newPrimary)
                        settings.saveBackgroundColor(newBg)
                        settings.saveSurfaceColor(newSurface)
                        recreate()
                    }
                )
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
    onColorsChange: (Color, Color, Color) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var selectedTab by rememberSaveable {
        mutableIntStateOf(activity?.intent?.getIntExtra("selected_tab", 0) ?: 0)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("📅", fontSize = 18.sp) },
                    label = { Text("График", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("💰", fontSize = 18.sp) },
                    label = { Text("Зарплата", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    label = { Text("Настройки", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("⏰", fontSize = 20.sp) },
                    label = { Text("Будильники", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Text("ℹ️", fontSize = 18.sp) },
                    label = { Text("О приложении", fontSize = 8.sp, maxLines = 1) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "tab_animation"
            ) { tab ->
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
                        currentSurfaceColor = surfaceColor
                    )
                    3 -> AlarmsTabScreen()  // <-- ИСПРАВЛЕНО: теперь без параметров
                    4 -> AboutScreen()
                }
            }
        }
    }
}