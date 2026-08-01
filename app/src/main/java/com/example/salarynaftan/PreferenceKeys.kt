package com.example.salarynaftan

object PreferenceKeys {

    const val ALARM_PREFS = "alarm_scheduler_prefs"

    const val REGULAR_ALARMS = "regular_alarms_list"

    const val AUTO_SILENCE_PREFS = "auto_silence_prefs"

    const val AUTO_SILENCE_ENABLED = "auto_silence_enabled"

    const val AUTO_SILENCE_START = "auto_silence_start"

    const val AUTO_SILENCE_END = "auto_silence_end"

    // Ниже — добавлено для единообразия с остальными файлами проекта
    // (раньше эти имена были "сырыми" строками в SettingsManager/MainActivity).

    const val SETTINGS_PREFS = "alarm_settings"

    const val COLOR_SETTINGS_PREFS = "color_settings"

    const val SALARY_MONTHS_PREFS = "salary_months_data"

    const val SALARY_HISTORY_PREFS = "salary_history_prefs"

    // Префиксы ключей для сменных будильников (динамические ключи с параметрами)
    const val SHIFT_ALARM_ENABLED_PREFIX = "shift_alarm_"
    const val SHIFT_TIMES_PREFIX = "shift_times_"

    // Префиксы ключей для данных зарплаты по месяцам (в SalaryCalculatorViewModel)
    const val NORM_PREFIX = "norm_"
    const val FACT_PREFIX = "fact_"
    const val NIGHT_PREFIX = "night_"
    const val S4_PREFIX = "s4_"
    const val ADV_PREFIX = "adv_"
    const val PRAZDN_PREFIX = "prazdn_"
    const val OTSUT_PREFIX = "otsut_"
    const val KVART_PREFIX = "kvart_"
    const val GAZ_PREFIX = "gaz_"
    const val POZ_PREFIX = "poz_"
    const val SUB_PREFIX = "sub_"
    const val SVOY_PREFIX = "svoy_"
    const val MMDETI_PREFIX = "mmdeti_"
    const val CHILDREN_PREFIX = "children_"

    // Ключи для данных SilentModeReceiver
    const val KEY_WAS_SILENCED_TODAY = "was_silenced_today"
    const val KEY_SAVED_INTERRUPTION_FILTER = "saved_interruption_filter"

    // Ключ для истории записей в HistoryManager
    const val KEY_HISTORY_RECORDS = "history_records"

    // Ключи для настроек SettingsManager
    const val KEY_VOLUME = "alarm_volume"
    const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
    const val KEY_IS_DARK = "is_dark_theme"
    const val KEY_PRIMARY_COLOR = "primary_color"
    const val KEY_BACKGROUND_COLOR = "background_color"
    const val KEY_SURFACE_COLOR = "surface_color"
    const val BRIGADE_KEY = "selected_brigade"

    // Строки действий (Intent.action) для авто-тишины. Технически это не
    // "ключи настроек", но раньше они были продублированы одинаковыми
    // строковыми литералами в AlarmScheduler.kt и SilentModeReceiver.kt —
    // при опечатке в одном месте фича молча ломается. Держим их тут же,
    // рядом с остальными общими константами.
    const val ACTION_SILENT_ON = "com.example.salarynaftan.ACTION_SILENT_ON"

    const val ACTION_SILENT_OFF = "com.example.salarynaftan.ACTION_SILENT_OFF"
}
