package com.example.salarynaftan

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.salarynaftan.ui.AppNavHost
import com.example.salarynaftan.ui.AppTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRate()
        // Разрешения вынесены в PermissionManager (п.3.1).
        permissionManager = PermissionManager(this)
        permissionManager.ensureNotificationPermission()
        permissionManager.ensureFullScreenIntentPermission()
        setContent {
            val settings = koinInject<SettingsManager>()
            // Тема и навигация вынесены в отдельные composable (п.3.1):
            // MainActivity остаётся тонким хостом.
            AppTheme(settings = settings) {
                AppNavHost(theme = this, permissionManager = permissionManager)
            }
        }
    }
}