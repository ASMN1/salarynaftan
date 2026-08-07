package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import android.app.Application
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class SettingsViewModel(
    application: Application,
    private val settingsManager: SettingsManager,
    private val colorSettings: ColorSettingsManager
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    data class SettingsUiState(
        val isDarkTheme: Boolean = true,
        val volume: Float = 0.7f,
        val ringtoneName: String = "По умолчанию",
        val ringtoneUri: Uri? = null,
        val brigade: Int = 1,
        val primaryColor: Color = Color(0xFF00E676),
        val backgroundColor: Color = Color(0xFF121212),
        val surfaceColor: Color = Color(0xFF1E1E1E),
        val morningColor: Color = Color(0xFFFEE45B),
        val dayColor: Color = Color(0xFFA2D39C),
        val nightColor: Color = Color(0xFF4F6D91),
        val offColor: Color = Color(0xFFF8EDF3),
        val isPlaying: Boolean = false
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                isDarkTheme = settingsManager.isDarkTheme(),
                volume = settingsManager.getVolume(),
                ringtoneName = settingsManager.getRingtoneName(),
                ringtoneUri = settingsManager.getRingtoneUri(),
                brigade = settingsManager.getBrigade(),
                primaryColor = settingsManager.getPrimaryColor(),
                backgroundColor = settingsManager.getBackgroundColor(),
                surfaceColor = settingsManager.getSurfaceColor(),
                morningColor = colorSettings.getMorningColor(),
                dayColor = colorSettings.getDayColor(),
                nightColor = colorSettings.getNightColor(),
                offColor = colorSettings.getOffColor()
            )
        }
    }

    fun setVolume(volume: Float) {
        _uiState.update { it.copy(volume = volume) }
        settingsManager.saveVolume(volume)
        if (isPlaying()) mediaPlayer?.setVolume(volume, volume)
    }

    fun setRingtoneUri(uri: Uri?) {
        settingsManager.saveRingtoneUri(uri?.toString())
        _uiState.update {
            it.copy(
                ringtoneUri = uri,
                ringtoneName = settingsManager.getRingtoneName()
            )
        }
    }

    fun setBrigade(brigade: Int, scheduler: AlarmScheduler) {
        _uiState.update { it.copy(brigade = brigade) }
        settingsManager.setBrigade(brigade)
        // Гасим сменные будильники всех бригад и оставляем только активной (п.4.4)
        scheduler.switchActiveBrigade(brigade)
    }

    fun setTheme(isDark: Boolean, onThemeChange: (Boolean) -> Unit) {
        val defaultPrimary = if (isDark) Color(0xFF00E676) else Color(0xFF00A859)
        val defaultBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
        val defaultSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
        val defaultMorning = Color(ShiftType.MORNING.defaultColorArgb)
        val defaultDay = Color(ShiftType.DAY.defaultColorArgb)
        val defaultNight = Color(ShiftType.NIGHT.defaultColorArgb)
        val defaultOff = Color(ShiftType.OFF.defaultColorArgb)

        settingsManager.savePrimaryColor(defaultPrimary)
        settingsManager.saveBackgroundColor(defaultBg)
        settingsManager.saveSurfaceColor(defaultSurface)
        colorSettings.saveMorningColor(defaultMorning)
        colorSettings.saveDayColor(defaultDay)
        colorSettings.saveNightColor(defaultNight)
        colorSettings.saveOffColor(defaultOff)

        _uiState.update {
            it.copy(
                isDarkTheme = isDark,
                primaryColor = defaultPrimary,
                backgroundColor = defaultBg,
                surfaceColor = defaultSurface,
                morningColor = defaultMorning,
                dayColor = defaultDay,
                nightColor = defaultNight,
                offColor = defaultOff
            )
        }
        onThemeChange(isDark)
    }

    fun setPrimaryColor(color: Color, onColorsChange: (Color, Color, Color) -> Unit) {
        settingsManager.savePrimaryColor(color)
        _uiState.update { it.copy(primaryColor = color) }
        onColorsChange(color, _uiState.value.backgroundColor, _uiState.value.surfaceColor)
    }

    fun setBackgroundColor(color: Color, onColorsChange: (Color, Color, Color) -> Unit) {
        settingsManager.saveBackgroundColor(color)
        _uiState.update { it.copy(backgroundColor = color) }
        onColorsChange(_uiState.value.primaryColor, color, _uiState.value.surfaceColor)
    }

    fun setSurfaceColor(color: Color, onColorsChange: (Color, Color, Color) -> Unit) {
        settingsManager.saveSurfaceColor(color)
        _uiState.update { it.copy(surfaceColor = color) }
        onColorsChange(_uiState.value.primaryColor, _uiState.value.backgroundColor, color)
    }

    fun setShiftColor(type: ShiftType, color: Color) {
        when (type) {
            ShiftType.MORNING -> {
                colorSettings.saveMorningColor(color)
                _uiState.update { it.copy(morningColor = color) }
            }
            ShiftType.DAY -> {
                colorSettings.saveDayColor(color)
                _uiState.update { it.copy(dayColor = color) }
            }
            ShiftType.NIGHT -> {
                colorSettings.saveNightColor(color)
                _uiState.update { it.copy(nightColor = color) }
            }
            ShiftType.OFF -> {
                colorSettings.saveOffColor(color)
                _uiState.update { it.copy(offColor = color) }
            }
        }
    }

    fun resetAllColors(onColorsChange: (Color, Color, Color) -> Unit) {
        val isDark = _uiState.value.isDarkTheme
        val defaultPrimary = if (isDark) Color(0xFF00E676) else Color(0xFF00A859)
        val defaultBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
        val defaultSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
        val defaultMorning = Color(ShiftType.MORNING.defaultColorArgb)
        val defaultDay = Color(ShiftType.DAY.defaultColorArgb)
        val defaultNight = Color(ShiftType.NIGHT.defaultColorArgb)
        val defaultOff = Color(ShiftType.OFF.defaultColorArgb)

        settingsManager.savePrimaryColor(defaultPrimary)
        settingsManager.saveBackgroundColor(defaultBg)
        settingsManager.saveSurfaceColor(defaultSurface)
        colorSettings.saveMorningColor(defaultMorning)
        colorSettings.saveDayColor(defaultDay)
        colorSettings.saveNightColor(defaultNight)
        colorSettings.saveOffColor(defaultOff)

        _uiState.update {
            it.copy(
                primaryColor = defaultPrimary,
                backgroundColor = defaultBg,
                surfaceColor = defaultSurface,
                morningColor = defaultMorning,
                dayColor = defaultDay,
                nightColor = defaultNight,
                offColor = defaultOff
            )
        }
        onColorsChange(defaultPrimary, defaultBg, defaultSurface)
    }

    fun playRingtone() {
        val uri = _uiState.value.ringtoneUri ?: return
        if (isPlaying()) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(getApplication(), uri)
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepare()
                setVolume(_uiState.value.volume, _uiState.value.volume)
                setOnCompletionListener { stopPlayback() }
                start()
            }
            _uiState.update { it.copy(isPlaying = true) }
        } catch (e: Exception) {
            Timber.e(e, "Не удалось воспроизвести рингтон")
            stopPlayback()
        }
    }

    fun stopPlayback() {
        // Сначала сбрасываем состояние, затем освобождаем ресурс —
        // это защищает от reentrancy, когда stopPlayback вызывается из
        // колбэка MediaPlayer (setOnErrorListener/setOnCompletionListener)
        // во время release (п.1.4).
        _uiState.update { it.copy(isPlaying = false) }
        val mp = mediaPlayer
        mediaPlayer = null
        try { mp?.let { if (it.isPlaying) it.stop(); it.release() } } catch (e: Exception) { Timber.e(e, "Ошибка освобождения mediaPlayer") }
    }

    fun isPlaying(): Boolean = _uiState.value.isPlaying

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
