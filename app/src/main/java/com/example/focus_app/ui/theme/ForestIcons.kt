package com.example.focus_app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ForestIcons {
    val Statistics: ImageVector by lazy {
        ImageVector.Builder("Statistics", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 13f); lineTo(7f, 13f); lineTo(7f, 22f); lineTo(3f, 22f); close()
                moveTo(10f, 7f); lineTo(14f, 7f); lineTo(14f, 22f); lineTo(10f, 22f); close()
                moveTo(17f, 2f); lineTo(21f, 2f); lineTo(21f, 22f); lineTo(17f, 22f); close()
            }
        }.build()
    }
}
