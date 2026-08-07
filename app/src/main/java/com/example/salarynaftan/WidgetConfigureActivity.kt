package com.example.salarynaftan

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigureActivity : AppCompatActivity() {

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

        val prefs = getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentBrigade = prefs.getInt(PreferenceKeys.BRIGADE_KEY, 1)

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

        val brigadeLabels = listOf(
            "Бригада 1",
            "Бригада 2",
            "Бригада 3",
            "Бригада 4",
            "Бригада 5"
        )

        for (i in 1..5) {
            val button = Button(this).apply {
                text = brigadeLabels[i - 1]
                textSize = 16f
                setOnClickListener {
                    // Единый источник бригады: SettingsManager.setBrigade обновляет
                    // и DataStore (приложение), и SharedPreferences (виджет), а также
                    // сам триггерит обновление виджета. Раньше писали только в
                    // SharedPreferences, из-за чего приложение оставалось со старой
                    // бригадой до перезапуска (рассинхрон источником).
                    SettingsManager(this@WidgetConfigureActivity).setBrigade(i)

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

            // Highlight current brigade
            if (i == currentBrigade) {
                button.isSelected = true
            }

            rootLayout.addView(button)
        }

        setContentView(rootLayout)
    }
}