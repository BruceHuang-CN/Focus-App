package com.example.focus_app.ui.reminder

import com.example.focus_app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.focus_app.ui.theme.FocusAppTheme
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt
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

internal fun breathingParticles(seed: Long, count: Int = 14): List<BreathingParticle> {
    val random = Random(seed)
    return List(count.coerceIn(0, 72)) {
        BreathingParticle(random.nextInt(4), 0.04f + random.nextFloat() * 0.92f,
            3f + random.nextFloat() * 3f, random.nextFloat() * PARTICLE_MAX_GATHER_STAGGER,
            2.6f + random.nextFloat() * 1.8f)
    }
}
internal const val MAX_BREATHING_TAPS = 40
internal const val BREATHING_LAST_TAP = .9f

internal fun recordBreathingTap(taps: List<Float>, progress: Float): List<Float> {
    if (progress !in 0f..BREATHING_LAST_TAP || taps.size >= MAX_BREATHING_TAPS) return taps
    // Ignore duplicate input within 80 ms; later taps remain effective throughout the exercise.
    if (taps.lastOrNull()?.let { progress - it < .016f } == true) return taps
    return taps + progress
}

internal fun boostedParticleProgress(
    t: Float, born: Float, taps: List<Float>, duration: Float = 1f - born
): Float {
    val span = duration.coerceAtLeast(.01f)
    val base = clamp01((t - born) / span)
    val impulse = taps.take(MAX_BREATHING_TAPS).sumOf {
        if (it >= born && it <= t) ((t - it) / span * .3f).toDouble() else 0.0
    }.toFloat()
    // A tap changes velocity smoothly, with a ceiling: it cannot consume the whole timeline.
    return base + (1f - base) * .28f * (1f - exp(-impulse))
}

internal data class BreathingFlight(
    val particle: BreathingParticle, val born: Float, val duration: Float, val interactive: Boolean
)

internal fun breathingFlights(seed: Long, taps: List<Float>): List<BreathingFlight> = buildList {
    fun wave(waveSeed: Long, born: Float, count: Int, interactive: Boolean) {
        breathingParticles(waveSeed, count).forEach { particle ->
            add(BreathingFlight(particle, born,
                minOf(.32f + particle.delay, .995f - born), interactive))
        }
    }
    // These waves keep arriving even when all early tap-generated particles have merged.
    repeat(8) { index -> wave(seed + index * 101L, index * .12f, if (index == 0) 10 else 4, false) }
    taps.take(MAX_BREATHING_TAPS).forEachIndexed { index, born ->
        if (born in 0f..BREATHING_LAST_TAP) wave(seed + 1_000L + index, born, 3, true)
    }
}

internal fun breathingFlightProgress(flight: BreathingFlight, t: Float, taps: List<Float>): Float =
    particleGatherProgress(
        boostedParticleProgress(t, flight.born, if (flight.interactive) taps else emptyList(), flight.duration),
        flight.particle.delay, flight.particle.exponent * .55f
    )

internal fun absorbedParticleWeight(gathered: Float): Float =
    easeInOutCubic(clamp01((gathered - .8f) / .2f))

internal fun breathingOrbRadiusDp(absorbed: Float): Float =
    sqrt(24f * 24f + absorbed.coerceAtLeast(0f) * 50f).coerceAtMost(96f)

internal fun orbPulse(t: Float): Float = 1f
internal fun orbFade(t: Float): Float = if (t < 1f) 1f else 0f

@Composable
internal fun ReminderGreenBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background, colors.surfaceContainerLow))), content = content)
}

/** Rendering reads the same five-second progress as the countdown. Taps never change it. */
@Composable
fun BreathingScreen(progress: State<Float>, modifier: Modifier = Modifier, animationKey: String = "preview") {
    val seed = rememberSaveable(animationKey) { Random.nextLong() }
    var taps by rememberSaveable(animationKey) { mutableStateOf(emptyList<Float>()) }
    BreathingContent(progress, seed, taps, onTap = {
        taps = recordBreathingTap(taps, progress.value)
    }, modifier = modifier)
}

@Composable
private fun BreathingContent(
    progress: State<Float>, seed: Long, taps: List<Float>, onTap: () -> Unit, modifier: Modifier = Modifier
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val flights = remember(seed, taps) { breathingFlights(seed, taps) }
    val gatheredProgress = remember(flights) { FloatArray(flights.size) }
    val colors = MaterialTheme.colorScheme
    val seconds by remember(progress) {
        derivedStateOf { ceil((1f - progress.value) * 5f).toInt().coerceIn(0, 5) }
    }
    ReminderGreenBackdrop(modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Button, onClickLabel = textContext.getString(R.string.polish_breath_tap), onClick = onTap
    )) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.weight(1f).fillMaxWidth()) {
                val t = progress.value
                if (t >= 1f || size.minDimension <= 0f) return@Canvas
                val center = Offset(size.width / 2f, size.height / 2f)
                var absorbed = 0f
                flights.forEachIndexed { index, flight ->
                    val gathered = breathingFlightProgress(flight, t, taps)
                    gatheredProgress[index] = gathered
                    absorbed += absorbedParticleWeight(gathered)
                }
                // Accumulated particle mass controls the area; the available space caps the radius.
                val orbRadius = minOf(breathingOrbRadiusDp(absorbed).dp.toPx(), size.minDimension * .28f)
                flights.forEachIndexed { index, flight ->
                    val gathered = gatheredProgress[index]
                    if (t <= flight.born || gathered >= 1f) return@forEachIndexed
                    val particle = flight.particle
                    val initialRadius = particle.radius.dp.toPx()
                    val inset = initialRadius + 3.dp.toPx()
                    val x = inset + (size.width - 2f * inset).coerceAtLeast(0f) * particle.fraction
                    val y = inset + (size.height - 2f * inset).coerceAtLeast(0f) * particle.fraction
                    val start = when (particle.edge) {
                        0 -> Offset(inset, y)
                        1 -> Offset(size.width - inset, y)
                        2 -> Offset(x, inset)
                        else -> Offset(x, size.height - inset)
                    }
                    val point = start + (center - start) * gathered
                    val merge = ((point - center).getDistance() / (orbRadius + initialRadius)).coerceIn(0f, 1f)
                    val radius = initialRadius * merge
                    val alpha = clamp01((t - flight.born) / .035f) * merge
                    drawCircle(colors.primary.copy(alpha = .12f * alpha), radius * 2.5f, point)
                    drawCircle(colors.primary.copy(alpha = .85f * alpha), radius, point)
                }
                drawCircle(colors.primary.copy(alpha = .055f), orbRadius * 1.7f, center)
                drawCircle(colors.primary.copy(alpha = .1f), orbRadius * 1.3f, center)
                // An immediate inward ring acknowledges each tap while its particles are travelling.
                taps.takeLast(4).forEach { tap ->
                    val age = (t - tap) / .085f
                    if (age in 0f..1f) drawCircle(colors.primary.copy(alpha = .25f * (1f - age)),
                        orbRadius + (1f - age) * 20.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                }
                drawCircle(Brush.radialGradient(listOf(colors.secondary, colors.primary),
                    center - Offset(orbRadius * .25f, orbRadius * .3f), orbRadius * 1.5f), orbRadius, center)
                drawCircle(colors.onPrimary.copy(alpha = .1f), orbRadius * .21f,
                    center - Offset(orbRadius * .32f, orbRadius * .32f))
            }
            Spacer(Modifier.height(16.dp))
            Text(textContext.getString(R.string.core_breathe), fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold, color = colors.onBackground, textAlign = TextAlign.Center)
            Text("$seconds", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Text(textContext.getString(R.string.core_breathe_help), color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(textContext.getString(R.string.polish_breath_tap), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Preview(name = "Breathing · no taps", widthDp = 393, heightDp = 800)
@Preview(name = "Breathing · dark", widthDp = 393, heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BreathingPreview() {
    FocusAppTheme { BreathingContent(remember { mutableStateOf(.88f) }, 42, emptyList(), {}) }
}

@Preview(name = "Breathing · many taps", widthDp = 393, heightDp = 800)
@Preview(name = "Breathing · narrow large text", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun BreathingManyTapsPreview() {
    FocusAppTheme { BreathingContent(remember { mutableStateOf(.88f) }, 42, List(25) { it * .028f }, {}) }
}
