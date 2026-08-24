package com.example.salarynaftan.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.salarynaftan.SettingsManager
import com.example.salarynaftan.ThemeDefaults

/**
 * Тема приложения (п.3.1): вынесена из MainActivity в отдельный composable.
 *
 * Управляет цветами (primary/background/surface), тёмной/светлой темой,
 * dynamic colors (Material You) и масштабом интерфейса. Возвращает колбэки
 * для обновления темы и цветов, чтобы экраны могли их менять без recreate.
 */
@Composable
fun AppTheme(
    settings: SettingsManager,
    content: @Composable AppThemeScope.() -> Unit
) {
    val context = LocalContext.current

    // Состояния цветов и темы (изменяемые без recreate)
    var isDarkTheme by remember { mutableStateOf(settings.isDarkTheme()) }
    var useDynamicColors by remember { mutableStateOf(settings.getUseDynamicColors()) }
    var useOled by remember { mutableStateOf(settings.getUseOled()) }

    var targetPrimary by remember { mutableStateOf(settings.getPrimaryColor()) }
    var targetBackground by remember { mutableStateOf(settings.getBackgroundColor()) }
    var targetSurface by remember { mutableStateOf(settings.getSurfaceColor()) }

    // SettingsManager является источником изменений не только для текущего
    // экрана настроек: например, тема может измениться из другого UI-потока.
    // Синхронизируем локальные Compose-состояния с его StateFlow.
    LaunchedEffect(settings) {
        launch { settings.isDarkThemeFlow.collect { isDarkTheme = it } }
        launch { settings.useDynamicColorsFlow.collect { useDynamicColors = it } }
        launch { settings.useOledFlow.collect { useOled = it } }
    }

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
    var uiScale by remember { mutableFloatStateOf(settings.getUiScale()) }
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
    } else if (isDarkTheme) {
        // OLED-режим: чисто чёрный фон (0xFF000000) экономит батарею на AMOLED
        // и даёт максимальный контраст. Поверхность тоже затемняется.
        val bg = if (useOled) Color.Black else animatedBackground
        val surface = if (useOled) Color(0xFF0A0A0A) else animatedSurface
        darkColorScheme(
            primary = animatedPrimary,
            background = bg,
            surface = surface,
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
        // Обновляем и primary, чтобы UI не расходился с сохранённым значением (БАГ-2).
        val newPrimary = ThemeDefaults.primary(isDark)
        val newBg = ThemeDefaults.background(isDark)
        val newSurface = ThemeDefaults.surface(isDark)
        targetPrimary = newPrimary
        targetBackground = newBg
        targetSurface = newSurface
        settings.savePrimaryColor(newPrimary)
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

    fun updateUiScale(newScale: Float) {
        uiScale = newScale
        settings.saveUiScale(newScale)
    }

    fun updateOled(newValue: Boolean) {
        useOled = newValue
        settings.saveUseOled(newValue)
    }

    // Когда Material You включен, primary/background/surface берутся из
    // динамической цветовой схемы (colorScheme), а не из кастомных значений.
    val effectivePrimary = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) colorScheme.primary else animatedPrimary
    val effectiveBackground = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) colorScheme.background else animatedBackground
    val effectiveSurface = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) colorScheme.surface else animatedSurface

    // Масштаб интерфейса применяется ко ВСЕМ вкладкам через LocalDensity.
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colorScheme) {
            content(
                AppThemeScope(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = ::updateTheme,
                    primaryColor = effectivePrimary,
                    backgroundColor = effectiveBackground,
                    surfaceColor = effectiveSurface,
                    onColorsChange = ::updateColors,
                    uiScale = uiScale,
                    onUiScaleChange = ::updateUiScale,
                    useOled = useOled,
                    onOledChange = ::updateOled
                )
            )
        }
    }
}

/** Параметры темы, доступные внутри [AppTheme]. */
data class AppThemeScope(
    val isDarkTheme: Boolean,
    val onThemeChange: (Boolean) -> Unit,
    val primaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val onColorsChange: (Color, Color, Color) -> Unit,
    val uiScale: Float,
    val onUiScaleChange: (Float) -> Unit,
    val useOled: Boolean,
    val onOledChange: (Boolean) -> Unit
)
