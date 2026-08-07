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

    // Префиксы ключей для сменных будильников (динамические ключи с параметрами)
    const val SHIFT_ALARM_ENABLED_PREFIX = "shift_alarm_"
    const val SHIFT_TIMES_PREFIX = "shift_times_"

    const val BRIGADE_KEY = "selected_brigade"

    // Ключи для данных SilentModeReceiver
    const val KEY_WAS_SILENCED_TODAY = "was_silenced_today"
    const val KEY_SAVED_INTERRUPTION_FILTER = "saved_interruption_filter"

    // Строки действий (Intent.action) для авто-тишины. Технически это не
    // "ключи настроек", но раньше они были продублированы одинаковыми
    // строковыми литералами в AlarmScheduler.kt и SilentModeReceiver.kt —
    // при опечатке в одном месте фича молча ломается. Держим их тут же,
    // рядом с остальными общими константами.
    const val ACTION_SILENT_ON = "com.example.salarynaftan.ACTION_SILENT_ON"

    const val ACTION_SILENT_OFF = "com.example.salarynaftan.ACTION_SILENT_OFF"

    // Пред-напоминание о смене (п.6.7): за сколько минут до сигнала показывать
    // уведомление-напоминание. Значение 0 — напоминание выключено.
    const val SHIFT_REMINDER_MINUTES = "shift_reminder_minutes"

    const val ACTION_SHIFT_REMINDER = "com.example.salarynaftan.ACTION_SHIFT_REMINDER"

    // Константы для данных, передаваемых в Intent пред-напоминания.
    const val EXTRA_REMINDER_TITLE = "reminder_title"
    const val EXTRA_REMINDER_SHIFT = "reminder_shift"
    const val EXTRA_REMINDER_TIME = "reminder_time"
}
