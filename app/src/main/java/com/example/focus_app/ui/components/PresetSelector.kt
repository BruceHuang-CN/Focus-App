package com.example.focus_app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 预设值选择器：展示一组预设标签，选中“自定义”后可以输入允许范围内的数值。
 *
 * @param presets 预设值列表，例如 [3, 10, 30]
 * @param customRange 自定义值允许的范围，例如 1..300
 * @param value 当前选中的值
 * @param formatPreset 将数值格式化为展示文本，例如 { it -> "${it} 秒" }
 * @param onValueChange 值变化回调（预设值或自定义值都会触发）
 */
@Composable
fun PresetSelector(
    presets: List<Int>,
    customRange: IntRange,
    value: Int,
    formatPreset: (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCustom = value !in presets
    var showCustom by remember { mutableStateOf(isCustom) }
    var customText by remember(value) { mutableStateOf(if (isCustom) value.toString() else "") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick = {
                        showCustom = false
                        onValueChange(preset)
                    },
                    label = { Text(formatPreset(preset)) }
                )
            }
            FilterChip(
                selected = isCustom,
                onClick = { showCustom = true },
                label = { Text("自定义") }
            )
        }
        if (showCustom) {
            OutlinedTextField(
                value = customText,
                onValueChange = { input ->
                    customText = input.filter { it.isDigit() }.take(5)
                    customText.toIntOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(customRange))
                    }
                },
                label = { Text("自定义数值") },
                suffix = { Text(formatPreset(value)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
