package com.example.focus_app.ui.onboarding

import com.example.focus_app.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.focus_app.domain.model.AppThemeMode
import com.example.focus_app.ui.theme.FocusAppTheme
import kotlin.math.min
import kotlin.math.sqrt

/** step 使用从 1 开始的页码；欢迎页也计入 total，传入 0 时显示尚未开始。 */
@Composable
fun TutorialHeader(
    title: String,
    subtitle: String,
    step: Int,
    total: Int = 7,
    welcome: Boolean = false
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    val pageCount = total.coerceAtLeast(1)
    val currentPage = step.coerceIn(0, pageCount)
    val progress = currentPage.toFloat() / pageCount

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = setupContext.getString(R.string.setup_text_001, currentPage, pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(colors.primary)
            )
        }
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = if (welcome) MaterialTheme.typography.headlineLarge
                else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            textAlign = if (welcome) TextAlign.Center else TextAlign.Start
        )
        Text(
            text = subtitle,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = if (welcome) TextAlign.Center else TextAlign.Start
        )
        TutorialPlant(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(if (welcome) 164.dp else 96.dp)
        )
    }
}

@Composable
fun TutorialCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun TutorialWelcomeContent() {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TutorialCard {
            TutorialWelcomeRow(Icons.Default.Info, setupContext.getString(R.string.setup_text_002), setupContext.getString(R.string.setup_text_003))
        }
        TutorialCard {
            TutorialWelcomeRow(Icons.Default.Add, setupContext.getString(R.string.setup_text_004), setupContext.getString(R.string.setup_text_005))
        }
        TutorialCard {
            TutorialWelcomeRow(Icons.Default.Check, setupContext.getString(R.string.setup_text_006), setupContext.getString(R.string.setup_text_007))
        }
    }
}

@Composable
private fun TutorialWelcomeRow(icon: ImageVector, title: String, description: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.onPrimaryContainer
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** 装饰性静态矢量，不额外占用无障碍焦点。 */
@Composable
private fun TutorialPlant(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val illustrationScale = min(size.width / 280f, size.height / 180f)
        withTransform({
            translate(
                left = (size.width - 280f * illustrationScale) / 2f,
                top = (size.height - 180f * illustrationScale) / 2f
            )
            scale(illustrationScale, illustrationScale, pivot = Offset.Zero)
        }) {
            val distantHill = Path().apply {
                moveTo(14f, 157f)
                cubicTo(73f, 106f, 131f, 111f, 193f, 143f)
                quadraticBezierTo(234f, 153f, 268f, 151f)
                lineTo(268f, 174f)
                lineTo(14f, 174f)
                close()
            }
            drawPath(distantHill, colors.secondaryContainer)
            val nearHill = Path().apply {
                moveTo(14f, 167f)
                cubicTo(76f, 167f, 129f, 133f, 181f, 140f)
                quadraticBezierTo(226f, 142f, 268f, 163f)
                lineTo(268f, 174f)
                lineTo(14f, 174f)
                close()
            }
            drawPath(nearHill, colors.primaryContainer)
            drawLine(colors.primary, Offset(140f, 147f), Offset(140f, 80f), 4f, StrokeCap.Round)
            tutorialLeaf(Offset(140f, 106f), Offset(98f, 59f), 27f, colors.secondary)
            tutorialLeaf(Offset(140f, 91f), Offset(181f, 41f), 29f, colors.primary)
        }
    }
}

private fun DrawScope.tutorialLeaf(base: Offset, tip: Offset, width: Float, color: Color) {
    val direction = tip - base
    val length = sqrt(direction.x * direction.x + direction.y * direction.y)
    val normal = Offset(-direction.y / length, direction.x / length) * width
    val middle = base + direction * 0.5f
    val first = middle + normal
    val second = middle - normal
    val leaf = Path().apply {
        moveTo(base.x, base.y)
        quadraticBezierTo(first.x, first.y, tip.x, tip.y)
        quadraticBezierTo(second.x, second.y, base.x, base.y)
        close()
    }
    drawPath(leaf, color)
}

@Preview(name = "教程欢迎 · 日间", widthDp = 360, showBackground = true)
@Composable
private fun TutorialWelcomeDayPreview() {
    TutorialComponentsPreview(AppThemeMode.DAY, welcome = true)
}

@Preview(name = "教程欢迎 · 夜间", widthDp = 360, showBackground = true)
@Composable
private fun TutorialWelcomeNightPreview() {
    TutorialComponentsPreview(AppThemeMode.NIGHT, welcome = true)
}

@Preview(name = "教程步骤 · 日间", widthDp = 360, showBackground = true)
@Composable
private fun TutorialStepDayPreview() {
    TutorialComponentsPreview(AppThemeMode.DAY, welcome = false)
}

@Preview(name = "教程步骤 · 夜间", widthDp = 360, showBackground = true)
@Composable
private fun TutorialStepNightPreview() {
    TutorialComponentsPreview(AppThemeMode.NIGHT, welcome = false)
}

@Composable
private fun TutorialComponentsPreview(mode: AppThemeMode, welcome: Boolean) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme(mode = mode) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TutorialHeader(
                    title = if (welcome) setupContext.getString(R.string.setup_text_008) else setupContext.getString(R.string.setup_text_009),
                    subtitle = if (welcome) setupContext.getString(R.string.setup_text_010) else setupContext.getString(R.string.setup_text_011),
                    step = if (welcome) 1 else 3,
                    welcome = welcome
                )
                TutorialWelcomeContent()
            }
        }
    }
}
