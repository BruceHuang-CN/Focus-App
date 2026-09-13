package com.example.focus_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.focus_app.domain.model.AppThemeColor
import com.example.focus_app.domain.model.AppThemeMode

private val MintLight = lightColorScheme(
    primary = Color(0xFF205C35), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEDD), onPrimaryContainer = Color(0xFF143E22),
    secondary = Color(0xFF42694D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EFE4), onSecondaryContainer = Color(0xFF173B24),
    background = Color(0xFFEDF6ED), onBackground = Color(0xFF102416),
    surface = Color.White, onSurface = Color(0xFF102416),
    surfaceVariant = Color(0xFFF2F6F2), onSurfaceVariant = Color(0xFF5E6D63),
    surfaceContainer = Color(0xFFF2F6F2), surfaceContainerLow = Color(0xFFF7FAF7),
    surfaceContainerHigh = Color(0xFFE7EFE7), surfaceContainerHighest = Color(0xFFE2ECE2),
    outline = Color(0xFF637667), outlineVariant = Color(0xFFCDDCCF),
    error = Color(0xFFB23A35)
)
private val MintDark = darkColorScheme(
    primary = Color(0xFF9CD4AA), onPrimary = Color(0xFF102A19),
    primaryContainer = Color(0xFF294C35), onPrimaryContainer = Color(0xFFD7F0DC),
    secondary = Color(0xFFAFCCB5), onSecondary = Color(0xFF193322),
    secondaryContainer = Color(0xFF334D3A), onSecondaryContainer = Color(0xFFD7EBDD),
    background = Color(0xFF101A14), onBackground = Color(0xFFE6EEE7),
    surface = Color(0xFF1A2820), onSurface = Color(0xFFE6EEE7),
    surfaceVariant = Color(0xFF23352A), onSurfaceVariant = Color(0xFFB1C0B5),
    surfaceContainer = Color(0xFF23352A), surfaceContainerLow = Color(0xFF1A2820),
    surfaceContainerHigh = Color(0xFF2A3E30), surfaceContainerHighest = Color(0xFF314737),
    outline = Color(0xFF8FA695), outlineVariant = Color(0xFF42574A),
    error = Color(0xFFFFB4AC)
)

private val BlueLight = lightColorScheme(
    primary = Color(0xFF2E5EAA), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3F9), onPrimaryContainer = Color(0xFF102F5C),
    secondary = Color(0xFF4A7BC4), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8FF), onSecondaryContainer = Color(0xFF12325F),
    tertiary = Color(0xFF2F80ED),
    background = Color(0xFFF5F8FD), onBackground = Color(0xFF191C22),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE3E9F3), onSurfaceVariant = Color(0xFF424B59),
    outline = Color(0xFF7B8493)
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFFA9C5FF), onPrimary = Color(0xFF123059),
    primaryContainer = Color(0xFF1D3F6E), onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFF8FB9FF), onSecondary = Color(0xFF0C2750),
    secondaryContainer = Color(0xFF1E4075), onSecondaryContainer = Color(0xFFD7E3FF),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF10151D), onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF161B24), onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF3A4350), onSurfaceVariant = Color(0xFFC0C8D4),
    outline = Color(0xFF8A94A2)
)

private val OrangeLight = lightColorScheme(
    primary = Color(0xFFC05A12), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC0), onPrimaryContainer = Color(0xFF5E2A04),
    secondary = Color(0xFFB97A1F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3B3), onSecondaryContainer = Color(0xFF5E3A00),
    tertiary = Color(0xFF2F80ED),
    background = Color(0xFFFDF8F2), onBackground = Color(0xFF201A15),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF201A15),
    surfaceVariant = Color(0xFFF1E7DC), onSurfaceVariant = Color(0xFF51473D),
    outline = Color(0xFF867B6F)
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB87A), onPrimary = Color(0xFF4A2204),
    primaryContainer = Color(0xFF71360A), onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFFFC46B), onSecondary = Color(0xFF4A2A00),
    secondaryContainer = Color(0xFF6E4200), onSecondaryContainer = Color(0xFFFFE3B3),
    tertiary = Color(0xFF8FB9FF),
    background = Color(0xFF1A150F), onBackground = Color(0xFFEDE4DA),
    surface = Color(0xFF211B15), onSurface = Color(0xFFEDE4DA),
    surfaceVariant = Color(0xFF453C33), onSurfaceVariant = Color(0xFFCFC3B6),
    outline = Color(0xFF998C7E)
)

private val GraphiteLight = lightColorScheme(
    primary = Color(0xFF2F6FBF), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E5FA), onPrimaryContainer = Color(0xFF12345E),
    secondary = Color(0xFF6E7F96), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EAF3), onSecondaryContainer = Color(0xFF2A3747),
    tertiary = Color(0xFF2F80ED),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE5E8ED), onSurfaceVariant = Color(0xFF44494F),
    outline = Color(0xFF7F858C)
)

private val GraphiteDark = darkColorScheme(
    primary = Color(0xFF9CC2F2), onPrimary = Color(0xFF133256),
    primaryContainer = Color(0xFF2A4A73), onPrimaryContainer = Color(0xFFD7E5FA),
    secondary = Color(0xFFA6B4C6), onSecondary = Color(0xFF1E2A38),
    secondaryContainer = Color(0xFF39465A), onSecondaryContainer = Color(0xFFD7E2F0),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF121417), onBackground = Color(0xFFE3E5E8),
    surface = Color(0xFF17191D), onSurface = Color(0xFFE3E5E8),
    surfaceVariant = Color(0xFF3A3E44), onSurfaceVariant = Color(0xFFC5C9CF),
    outline = Color(0xFF8E939A)
)

@Composable
fun FocusAppTheme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    color: AppThemeColor = AppThemeColor.MINT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DAY -> false
        AppThemeMode.NIGHT -> true
    }
    val colorScheme = when (color) {
        AppThemeColor.MINT -> if (darkTheme) MintDark else MintLight
        AppThemeColor.BLUE -> if (darkTheme) BlueDark else BlueLight
        AppThemeColor.ORANGE -> if (darkTheme) OrangeDark else OrangeLight
        AppThemeColor.GRAPHITE -> if (darkTheme) GraphiteDark else GraphiteLight
    }
    MaterialTheme(
        colorScheme = colorScheme, typography = AppTypography,
        shapes = androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
        )
    ) {
        ForestSystemBars(darkTheme)
        content()
    }
}
