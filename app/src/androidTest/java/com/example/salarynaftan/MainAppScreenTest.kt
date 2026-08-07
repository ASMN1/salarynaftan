package com.example.salarynaftan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.salarynaftan.ui.PremiumButton
import com.example.salarynaftan.ui.PremiumHeader
import com.example.salarynaftan.ui.ShimmerList
import org.junit.Rule
import org.junit.Test

/**
 * UI-тесты Compose: проверяют отображение экранов и компонентов без краша
 * и корректное взаимодействие с полями/элементами.
 *
 * Выполняются на устройстве/эмуляторе (androidTest). Покрываем
 * самодостаточные компоненты (без Koin), чтобы тесты были стабильными.
 */
class MainAppScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ===== PremiumComponents: отображение =====

    @Test
    fun premiumHeader_rendersTitleAndSubtitle() {
        composeRule.setContent {
            MaterialTheme {
                PremiumHeader(title = "График", subtitle = "Бригада 3")
            }
        }
        composeRule.onNodeWithText("График").assertIsDisplayed()
        composeRule.onNodeWithText("Бригада 3").assertIsDisplayed()
    }

    @Test
    fun premiumHeader_withoutSubtitle_showsTitleOnly() {
        composeRule.setContent {
            MaterialTheme {
                PremiumHeader(title = "Настройки")
            }
        }
        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
    }

    // ===== Shimmer: плейсхолдер отображается без краша =====

    @Test
    fun shimmerList_rendersWithoutCrash() {
        composeRule.setContent {
            MaterialTheme {
                ShimmerList(rows = 3)
            }
        }
        // Shimmer не содержит текста — просто проверяем, что дерево строится.
        composeRule.onRoot().assertExists()
    }

    // ===== PremiumButton: клик =====

    @Test
    fun premiumButton_click_triggerCallback() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                PremiumButton(
                    text = "Сохранить",
                    onClick = { clicked = true }
                )
            }
        }
        composeRule.onNodeWithText("Сохранить").performClick()
        // compose-правило синхронно обновляет состояние Compose, но не
        // перерисовывает внешнюю переменную — проверяем через assert.
        composeRule.runOnIdle { assert(clicked) }
    }

    // ===== Поле ввода: взаимодействие =====

    @Test
    fun textField_acceptsInput() {
        composeRule.setContent {
            MaterialTheme {
                var value by mutableStateOf("")
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("Введите оклад") },
                    label = { Text("Оклад") }
                )
            }
        }
        composeRule.onNodeWithText("Оклад").performTextInput("1607.93")
        composeRule.onNodeWithText("Оклад").assertTextContains("1607.93")
    }
}
