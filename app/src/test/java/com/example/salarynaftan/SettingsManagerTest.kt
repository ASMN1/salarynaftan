package com.example.salarynaftan

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.salarynaftan.util.colorToArgb
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты SettingsManager: прокси-слой над DataStoreManager.
 * Проверяем конвертацию Color<->ARGB, дефолты, кэш и границы значений.
 *
 * Как и в DataStoreManagerTest, сначала читаем (загружаем кэш), затем
 * сохраняем — повторное чтение идёт из кэша. Это соответствует реальному
 * использованию (чтения настроек при старте идут до сохранений).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class SettingsManagerTest {

    private lateinit var context: Context
    private lateinit var manager: SettingsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Очищаем файл DataStore — иначе значения «протекают» между тестами.
        DataStoreManagerTest.clearDataStore(context)
        // Сбрасываем singleton-кэш: удаление файла не меняет уже загруженные
        // значения существующего DataStoreManager.
        com.example.salarynaftan.data.DataStoreManager.clearInstances()
        manager = SettingsManager(context)
        // График №2 ограничивает бригаду четырьмя; явно фиксируем базовый
        // график тестов, чтобы результат не зависел от порядка тестов.
        manager.setScheduleType(ScheduleType.GRAPH_1)
    }

    @Test
    fun `ui scale is clamped to 0_7 to 1_5 through manager`() {
        manager.getUiScale() // загружаем кэш
        manager.saveUiScale(0.3f)
        assertEquals(0.7f, manager.getUiScale(), 0.0001f)

        manager.saveUiScale(8f)
        assertEquals(1.5f, manager.getUiScale(), 0.0001f)

        manager.saveUiScale(1.2f)
        assertEquals(1.2f, manager.getUiScale(), 0.0001f)
    }

    @Test
    fun `volume ramp sec is clamped to 2 to 30 through manager`() {
        manager.getVolumeRampSec() // загружаем кэш
        manager.saveVolumeRampSec(1)
        assertEquals(2, manager.getVolumeRampSec())

        manager.saveVolumeRampSec(45)
        assertEquals(30, manager.getVolumeRampSec())
    }

    @Test
    fun `salary preserves precision`() {
        manager.getSalary() // загружаем кэш
        manager.saveSalary(1607.93)
        assertEquals(1607.93, manager.getSalary(), 0.0000001)
    }

    @Test
    fun `color round-trips to argb and back`() {
        manager.getPrimaryColor() // загружаем кэш
        val color = Color(0xFF336699)
        manager.savePrimaryColor(color)
        assertEquals(colorToArgb(color), colorToArgb(manager.getPrimaryColor()))
    }

    @Test
    fun `background and surface colors round-trip`() {
        manager.getBackgroundColor(); manager.getSurfaceColor() // загружаем кэш
        manager.saveBackgroundColor(Color(0xFF101010))
        manager.saveSurfaceColor(Color(0xFF202020))
        assertEquals(colorToArgb(Color(0xFF101010)), colorToArgb(manager.getBackgroundColor()))
        assertEquals(colorToArgb(Color(0xFF202020)), colorToArgb(manager.getSurfaceColor()))
    }

    @Test
    fun `brigade is clamped to 1 to 5 through manager`() {
        manager.getBrigade() // загружаем кэш
        manager.setBrigade(0)
        assertEquals(1, manager.getBrigade())

        manager.setBrigade(99)
        assertEquals(5, manager.getBrigade())
    }

    @Test
    fun `setBrigade writes to shared preferences for widget`() {
        manager.setBrigade(3)
        val prefs = context.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        assertEquals(3, prefs.getInt(PreferenceKeys.BRIGADE_KEY, -1))
    }

    @Test
    fun `coefs round-trip through float`() {
        manager.getPremiumCoef(); manager.getStazhKoef() // загружаем кэш
        manager.savePremiumCoef(0.6)
        assertEquals(0.6, manager.getPremiumCoef(), 0.0001)

        manager.saveStazhKoef(0.35)
        assertEquals(0.35, manager.getStazhKoef(), 0.0001)
    }

    @Test
    fun `pps percent is clamped to 0 to 100 through manager`() {
        manager.getPpsPercent() // загружаем кэш
        manager.savePpsPercent(150.0)
        assertEquals(100.0, manager.getPpsPercent(), 0.0001)
    }

    @Test
    fun `theme round-trips`() {
        manager.isDarkTheme() // загружаем кэш
        manager.saveTheme(false)
        assertEquals(false, manager.isDarkTheme())
        manager.saveTheme(true)
        assertEquals(true, manager.isDarkTheme())
    }

    @Test
    fun `volume round-trips`() {
        manager.getVolume() // загружаем кэш
        manager.saveVolume(0.35f)
        assertEquals(0.35f, manager.getVolume(), 0.0001f)
    }
}