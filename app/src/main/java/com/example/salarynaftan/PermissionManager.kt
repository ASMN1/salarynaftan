package com.example.salarynaftan

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Централизованная работа с разрешениями (п.3.1).
 *
 * Раньше логика запроса разрешений (уведомления, full-screen intent) лежала
 * прямо в MainActivity, раздувая её. Вынесена в отдельный класс, чтобы
 * Activity оставалась тонким хостом, а разрешения можно было переиспользовать.
 */
class PermissionManager(private val activity: ComponentActivity) {

    private val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не важен */ }

    private val calendarPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не важен */ }

    /** Запрашивает разрешение на уведомления (Android 13+). */
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Возвращает true, если разрешение на запись в календарь выдано. */
    fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Запрашивает разрешение на запись в календарь (для синхронизации смен). */
    fun requestCalendarPermission() {
        if (!hasCalendarPermission()) {
            calendarPermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }

    /** Направляет пользователя в настройки full-screen intent (Android 14+). */
    fun ensureFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        val nm = activity.getSystemService(NotificationManager::class.java)
        if (!nm.canUseFullScreenIntent()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = android.net.Uri.fromParts("package", activity.packageName, null)
            }
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
                try { activity.startActivity(fallback) } catch (_: Exception) {}
            }
        }
    }
}