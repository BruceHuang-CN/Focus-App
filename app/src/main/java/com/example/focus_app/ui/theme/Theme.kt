package com.example.focus_app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = InkBlue, onPrimary = WarmWhite, background = Parchment, onBackground = WarmGray,
    surface = WarmWhite, onSurface = WarmGray, outline = BorderGray,
)

@Composable
fun FocusAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, typography = AppTypography, content = content)
}
