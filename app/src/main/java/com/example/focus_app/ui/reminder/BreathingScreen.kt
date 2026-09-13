package com.example.focus_app.ui.reminder

import com.example.focus_app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.focus_app.ui.theme.FocusAppTheme
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random
import androidx.compose.runtime.saveable.rememberSaveable

internal const val BREATHING_TOTAL_MS = 5_000L
internal const val PHASE_GATHER_START = 0f
internal const val PHASE_GATHER_END = 1f
internal const val PHASE_BALL_START = 0f
internal const val PHASE_BALL_END = 1f
internal const val PHASE_FADE_START = 1f
internal const val PHASE_FADE_END = 1f
internal const val PARTICLE_MAX_GATHER_STAGGER = 0.05f
internal fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
internal fun easeInOutCubic(t: Float): Float {
    val c = clamp01(t)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3f) / 2f
}
internal fun easeOutCubic(t: Float): Float = 1f - (1f - clamp01(t)).pow(3f)
internal fun particleGatherProgress(t: Float, stagger: Float, exponent: Float = 3f): Float =
    clamp01((t - stagger) / (1f - stagger)).pow(exponent)

internal data class BreathingParticle(val edge: Int, val fraction: Float, val radius: Float,
    val delay: Float, val exponent: Float)

internal fun breathingParticles(seed: Long): List<BreathingParticle> {
    val random = Random(seed)
    return List(14) {
        BreathingParticle(random.nextInt(4), 0.04f + random.nextFloat() * 0.92f,
            3f + random.nextFloat() * 3f, random.nextFloat() * PARTICLE_MAX_GATHER_STAGGER,
            2.6f + random.nextFloat() * 1.8f)
    }
}
internal fun orbPulse(t: Float): Float = 1f
internal fun orbFade(t: Float): Float = if (t < 1f) 1f else 0f

@Composable
internal fun ReminderGreenBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background, colors.surfaceContainerLow))), content = content)
}

/** Rendering reads the same progress as the countdown; no second animation clock. */
@Composable
fun BreathingScreen(progress: State<Float>, modifier: Modifier = Modifier, animationKey: String = "preview") {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val seed = rememberSaveable(animationKey) { Random.nextLong() }
    val particles = remember(seed) { breathingParticles(seed) }
    val colors = MaterialTheme.colorScheme
    val seconds by remember(progress) { derivedStateOf { ceil((1f - progress.value) * 5f).toInt().coerceIn(0, 5) } }
    ReminderGreenBackdrop(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            if (t >= 1f) return@Canvas
            val center = Offset(size.width / 2f, size.height * 0.37f)
            val orbRadius = 32.dp.toPx() * t.coerceAtLeast(0.02f)
            particles.forEach { particle ->
                val initialRadius = particle.radius.dp.toPx()
                val inset = initialRadius + 3f
                val x = inset + (size.width - 2f * inset).coerceAtLeast(0f) * particle.fraction
                val y = inset + (size.height - 2f * inset).coerceAtLeast(0f) * particle.fraction
                val start = when (particle.edge) {
                    0 -> Offset(inset, y)
                    1 -> Offset(size.width - inset, y)
                    2 -> Offset(x, inset)
                    else -> Offset(x, size.height - inset)
                }
                val gathered = particleGatherProgress(t, particle.delay, particle.exponent)
                val point = start + (center - start) * gathered
                // Only soften/shrink at the orb surface: visible acceleration becomes a merge.
                val merge = ((point - center).getDistance() / (orbRadius + initialRadius)).coerceIn(0f, 1f)
                val radius = initialRadius * merge
                val alpha = clamp01((t - particle.delay) / 0.12f) * merge
                drawCircle(colors.primary.copy(alpha = 0.10f * alpha), radius * 2.5f, point)
                drawCircle(colors.primary.copy(alpha = 0.8f * alpha), radius, point)
            }
            val radius = 32.dp.toPx() * t.coerceAtLeast(0.02f)
            drawCircle(colors.primary.copy(alpha = 0.055f), radius * 2.4f, center)
            drawCircle(colors.primary.copy(alpha = 0.08f), radius * 1.65f, center)
            drawCircle(Brush.radialGradient(listOf(colors.primary, colors.secondary), center, radius), radius, center)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text(textContext.getString(R.string.core_breathe), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Text("$seconds", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Text(textContext.getString(R.string.core_breathe_help), color = colors.onSurfaceVariant)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Preview(widthDp = 393, heightDp = 800)
@Preview(widthDp = 393, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BreathingPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme { BreathingScreen(remember { mutableStateOf(0.6f) }) }
}
