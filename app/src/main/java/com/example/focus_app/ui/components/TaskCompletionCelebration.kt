package com.example.focus_app.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.focus_app.R
import com.example.focus_app.domain.model.AppThemeColor
import com.example.focus_app.domain.model.AppThemeMode
import com.example.focus_app.ui.theme.FocusAppTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** One celebration per successful action. Its own window covers the bottom navigation too. */
@Composable
fun TaskCompletionCelebration(eventId: Int) {
    val progress = remember { Animatable(1f) }
    var visible by remember { mutableStateOf(false) }
    val motionEnabled = remember(eventId) { ValueAnimator.areAnimatorsEnabled() }
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(eventId) {
        if (eventId == 0) return@LaunchedEffect
        val duration = accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = 4_000L,
            containsIcons = true, containsText = true, containsControls = true
        ) ?: 4_000L
        progress.snapTo(if (motionEnabled) 0f else 0.5f)
        visible = true
        if (motionEnabled) {
            progress.animateTo(0.9f, tween(3_600, easing = LinearEasing))
            delay((duration - 4_000L).coerceAtLeast(0L))
            progress.animateTo(1f, tween(400, easing = LinearEasing))
        } else {
            // Animator scale zero must still leave enough time to read the feedback.
            delay(duration)
        }
        visible = false
    }
    if (!visible) return
    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        CelebrationScene(
            progress = { progress.value },
            motionEnabled = motionEnabled,
            onContinue = { visible = false }
        )
    }
}

@Composable
private fun CelebrationScene(
    progress: () -> Float,
    motionEnabled: Boolean,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val continueLabel = stringResource(R.string.polish_celebration_continue)
    val particles = remember { celebrationParticles() }
    val ribbonPath = remember { Path() }
    val palette = remember(colors) {
        listOf(colors.primary, colors.tertiary, Color(0xFFFFBF47), Color(0xFFFF7E8B),
            Color(0xFF69BFF0), Color(0xFFB99AF7))
    }
    Box(
        Modifier.fillMaxSize().testTag("completion-celebration")
            .graphicsLayer {
                val p = progress()
                alpha = if (motionEnabled) minOf((p / 0.045f).coerceIn(0f, 1f),
                    ((1f - p) / 0.1f).coerceIn(0f, 1f)) else 1f
            }
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background,
                colors.background, colors.primaryContainer.copy(alpha = 0.65f))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClickLabel = continueLabel, onClick = onContinue
            ),
        contentAlignment = Alignment.Center
    ) {
        if (motionEnabled) {
            Canvas(Modifier.matchParentSize()) {
                val seconds = progress() * 4f
                particles.forEach { particle ->
                    val age = seconds - particle.delay
                    if (age <= 0f || age >= particle.lifetime) return@forEach
                    val fade = ((particle.lifetime - age) / 0.6f).coerceIn(0f, 1f)
                    val x = size.width * (particle.origin + particle.vx *
                        (1f - exp(-age * 0.9f)) / 0.9f) + sin(age * 5f + particle.phase) * 9.dp.toPx() * age
                    val y = size.height + 24.dp.toPx() - size.height * particle.lift * age +
                        size.height * 0.9f * age * age
                    val color = palette[particle.color].copy(alpha = fade)
                    if (particle.ribbon) {
                        val length = particle.length.dp.toPx()
                        val path = ribbonPath.apply { reset() }
                        repeat(17) { segment ->
                            val fraction = segment / 16f
                            val px = x + sin(fraction * PI.toFloat() * 2.3f + age * 7f + particle.phase) *
                                9.dp.toPx() * fraction
                            val py = y + fraction * length
                            if (segment == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(path, color, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        rotate(particle.phase * 57.3f + age * particle.spin, Offset(x, y)) {
                            val width = particle.length.dp.toPx()
                            val height = 5.dp.toPx() * (0.3f + 0.7f * kotlin.math.abs(cos(age * 9f + particle.phase)))
                            drawRoundRect(color, Offset(x - width / 2f, y - height / 2f), Size(width, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(horizontal = 28.dp, vertical = 32.dp)
                    .graphicsLayer {
                        val arrival = if (motionEnabled) ((progress() - 0.035f) / 0.16f).coerceIn(0f, 1f) else 1f
                        val shift = arrival - 1f
                        val spring = 1f + 2.4f * shift * shift * shift + 1.4f * shift * shift
                        translationY = (1f - spring) * 80.dp.toPx()
                        scaleX = 0.86f + 0.14f * spring
                        scaleY = scaleX
                        alpha = (arrival / 0.45f).coerceIn(0f, 1f)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SuccessMedal(Modifier.size(132.dp))
                Spacer(Modifier.height(24.dp))
                Column(
                    Modifier.widthIn(max = 420.dp).semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    }, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.polish_celebration_title), color = colors.primary,
                        fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.polish_celebration_body), color = colors.onBackground,
                        fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(40.dp))
                Text(continueLabel, color = colors.onSurfaceVariant, fontSize = 16.sp,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SuccessMedal(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val radius = size.minDimension * 0.34f
        drawCircle(colors.primary.copy(alpha = 0.08f), size.minDimension * 0.5f)
        drawCircle(colors.primary.copy(alpha = 0.1f), size.minDimension * 0.43f)
        drawCircle(colors.primary.copy(alpha = 0.25f), radius, center + Offset(0f, 6.dp.toPx()))
        drawCircle(colors.primary, radius)
        drawArc(colors.onPrimary.copy(alpha = 0.25f), 205f, 105f, false,
            center - Offset(radius * 0.8f, radius * 0.8f), Size(radius * 1.6f, radius * 1.6f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val check = Path().apply {
            moveTo(center.x - radius * 0.42f, center.y)
            lineTo(center.x - radius * 0.1f, center.y + radius * 0.3f)
            lineTo(center.x + radius * 0.44f, center.y - radius * 0.3f)
        }
        drawPath(check, colors.onPrimary, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round))
        listOf(Offset(0.12f, 0.2f), Offset(0.9f, 0.28f), Offset(0.84f, 0.83f)).forEachIndexed { index, point ->
            val c = Offset(size.width * point.x, size.height * point.y)
            val r = (if (index == 0) 7 else 5).dp.toPx()
            val star = Path().apply {
                moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y)
                quadraticTo(c.x, c.y, c.x, c.y + r); quadraticTo(c.x, c.y, c.x - r, c.y)
                quadraticTo(c.x, c.y, c.x, c.y - r); close()
            }
            drawPath(star, if (index == 1) colors.tertiary else Color(0xFFF5B83D))
        }
    }
}

private data class CelebrationParticle(
    val origin: Float, val vx: Float, val lift: Float, val delay: Float, val lifetime: Float,
    val color: Int, val ribbon: Boolean, val length: Float, val spin: Float, val phase: Float
)

private fun celebrationParticles(): List<CelebrationParticle> {
    val random = Random(42)
    return List(88) { index ->
        val left = index % 2 == 0
        val ribbon = index % 5 == 0
        CelebrationParticle(
            origin = if (left) 0.06f else 0.94f,
            vx = (if (left) 1f else -1f) * (0.12f + random.nextFloat() * 0.72f),
            lift = 1.58f + random.nextFloat() * 0.42f,
            delay = random.nextFloat() * 0.32f + if (index > 64) 0.28f else 0f,
            lifetime = 2.3f + random.nextFloat() * 0.6f,
            color = index % 6, ribbon = ribbon,
            length = if (ribbon) 44f + random.nextFloat() * 50f else 7f + random.nextFloat() * 6f,
            spin = (random.nextFloat() - 0.5f) * 720f, phase = random.nextFloat() * 2f * PI.toFloat()
        )
    }
}

@Preview(name = "Celebration · 393dp", widthDp = 393, heightDp = 852)
@Composable
private fun CelebrationPreview() {
    FocusAppTheme { CelebrationScene(progress = { 0.28f }, motionEnabled = true, onContinue = {}) }
}

@Preview(name = "Celebration · narrow large text", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun CelebrationNarrowPreview() {
    FocusAppTheme { CelebrationScene(progress = { 0.28f }, motionEnabled = true, onContinue = {}) }
}

@Preview(name = "Celebration · dark orange", widthDp = 393, heightDp = 852)
@Composable
private fun CelebrationDarkPreview() {
    FocusAppTheme(mode = AppThemeMode.NIGHT, color = AppThemeColor.ORANGE) {
        CelebrationScene(progress = { 0.28f }, motionEnabled = true, onContinue = {})
    }
}
