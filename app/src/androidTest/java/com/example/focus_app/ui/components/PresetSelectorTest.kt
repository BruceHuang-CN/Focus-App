package com.example.focus_app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun presets_are_shown_and_clicking_updates_value() {
        var selected = 3
        composeRule.setContent {
            PresetSelector(
                presets = listOf(3, 10, 30),
                customRange = 1..300,
                value = selected,
                formatPreset = { "${it} 秒" },
                onValueChange = { selected = it }
            )
        }

        composeRule.onNodeWithText("3 秒").assertIsDisplayed()
        composeRule.onNodeWithText("10 秒").performClick()
        composeRule.runOnIdle { assertEquals(10, selected) }
    }

    @Test
    fun custom_chip_reveals_input_field() {
        composeRule.setContent {
            PresetSelector(
                presets = listOf(3, 10, 30),
                customRange = 1..300,
                value = 3,
                formatPreset = { "${it} 秒" },
                onValueChange = {}
            )
        }

        composeRule.onNodeWithText("自定义").performClick()
        composeRule.onNodeWithText("自定义数值").assertIsDisplayed()
    }
}
