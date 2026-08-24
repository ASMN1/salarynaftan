package com.example.salarynaftan

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.salarynaftan.di.AppDependencies

/**
 * Экран настройки виджета (выбор бригады).
 *
 * Наследует обычный [Activity], а не AppCompatActivity: в манифесте ему
 * назначена тема `android:Theme.Material.Light.NoActionBar`, а AppCompatActivity
 * требует AppCompat-тему и падал бы с IllegalArgumentException при добавлении
 * виджета. Вид строится программно, поэтому AppCompat не нужен.
 */
class WidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Validate widget id
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        // Диапазон бригад зависит от активного графика (№1 — 5 бригад, №2 — 4).
        val settings = AppDependencies.settingsManager
        val scheduleType = settings.getScheduleType()
        val currentBrigade = settings.getBrigade()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = "Выберите бригаду"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(titleView)

        val brigadeLabels = (1..scheduleType.brigadeCount).map { "Бригада $it" }

        for (i in 1..scheduleType.brigadeCount) {
            val isCurrent = i == currentBrigade
            val button = Button(this).apply {
                text = brigadeLabels[i - 1]
                textSize = 16f
                // Визуально выделяем текущую бригаду (ПОТЕНЦ-4): у Button нет
                // селектора для isSelected, поэтому меняем фон и цвет текста.
                if (isCurrent) {
                    setBackgroundColor(0xFF00E676.toInt())
                    setTextColor(android.graphics.Color.BLACK)
                }
                setOnClickListener {
                    // Единый источник бригады: SettingsManager.setBrigade обновляет
                    // и DataStore (приложение), и SharedPreferences (виджет), а также
                    // сам триггерит обновление виджета. Раньше писали только в
                    // SharedPreferences, из-за чего приложение оставалось со старой
                    // бригадой до перезапуска (рассинхрон источником).
                    settings.setBrigade(i)

                    // Return OK
                    val resultValue = Intent().apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    setResult(RESULT_OK, resultValue)
                    finish()
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            button.layoutParams = params

            rootLayout.addView(button)
        }

        setContentView(rootLayout)
    }
}