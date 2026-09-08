package com.example.focus_app.ui.reminder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// 呼吸/决策页共用的固定绿色系（设计稿配色），不随应用主题切换。
private val BreathingBgTop = Color(0xFFF4F9F5)
private val BreathingBgBottom = Color(0xFFE7F2EA)
internal val BreathingDeep = Color(0xFF0E7C5E)
internal val BreathingOrbCore = Color(0xFF2FA36B)
private val BreathingOrbCoreLight = Color(0xFF4CC57F)
private val BreathingSubtitle = Color(0xFF7C8B81)
private val BreathingQuoteText = Color(0xFF55685C)
private val BreathingQuoteBg = Color(0xFFEFF6F1)

internal const val BREATHING_TOTAL_MS = 5_000L
private const val PARTICLE_COUNT = 10

// 时间轴各阶段（占 5 秒的比例）：渐现 0~0.24，聚集 0.24~0.56，
// 成球 0.52~0.68，保持脉动 0.68~0.92，渐隐 0.92~1.0。
internal const val PHASE_GATHER_START = 0.24f
internal const val PHASE_GATHER_END = 0.56f
internal const val PHASE_BALL_START = 0.52f
internal const val PHASE_BALL_END = 0.68f
internal const val PHASE_FADE_START = 0.92f
internal const val PHASE_FADE_END = 1f

internal fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

internal fun easeInOutCubic(t: Float): Float {
    val c = clamp01(t)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3f) / 2f
}

internal fun easeOutCubic(t: Float): Float {
    val c = clamp01(t)
    return 1f - (1f - c).pow(3f)
}

// 大球在保持阶段（成球→渐隐）做一个完整呼吸脉动，两端相位为 0，衔接连续。
internal fun orbPulse(t: Float): Float =
    1f + 0.035f * sin(clamp01((t - PHASE_BALL_END) / (PHASE_FADE_START - PHASE_BALL_END)) * 2.0 * PI).toFloat()

// 大球/圆环整体渐隐系数，1 = 完全可见。
internal fun orbFade(t: Float): Float =
    1f - clamp01((t - PHASE_FADE_START) / (PHASE_FADE_END - PHASE_FADE_START))

// 粒子出现/聚集的随机范围上限（供测试引用，保持单一事实来源）。
internal const val PARTICLE_MAX_GATHER_STAGGER = 0.05f

// 小球向中心聚集的进度：0 = 还在起点，1 = 到达中心。与绘制实现共用，供测试。
internal fun particleGatherProgress(t: Float, stagger: Float): Float {
    // stagger 必然远小于聚集跨度（0.32），分母恒正，不会除零。
    return easeInOutCubic(
        clamp01(
            (t - PHASE_GATHER_START - stagger) /
                (PHASE_GATHER_END - PHASE_GATHER_START - stagger)
        )
    )
}

/**
 * 呼吸页与决策页共用的浅绿渐变背景（含角落装饰弧面）。
 * 单一实现，两页观感一致，改色只动这里。
 */
@Composable
internal fun ReminderGreenBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BreathingBgTop, BreathingBgBottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBackdropDecorations()
        }
        content()
    }
}

/**
 * 深呼吸页：浅绿背景 + 小球从周围随机出现并向中心聚集，
 * 融合成大球轻脉动，最后整体渐隐。倒计时数字由外部 [step] 驱动。
 */
@Composable
fun BreathingScreen(step: Int, modifier: Modifier = Modifier) {
    // 中途旋转/重建组合时按当前 step 恢复动画相位，不重播；只在首次组合时算一次。
    val startFraction = remember { ((5 - step) / 5f).coerceIn(0f, 1f) }
    ReminderGreenBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.62f))
            BreathingOrb(startFraction = startFraction, modifier = Modifier.size(320.dp))
            Spacer(Modifier.height(30.dp))
            Text(
                text = "深呼吸一下",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = BreathingDeep
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${step.coerceAtLeast(1)}",
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
                color = BreathingDeep
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "让注意力慢慢回到此刻", fontSize = 16.sp, color = BreathingSubtitle)
            Spacer(Modifier.weight(1f))
            BreathingQuote()
        }
    }
}

@Composable
private fun BreathingOrb(startFraction: Float, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(startFraction) }
    LaunchedEffect(Unit) {
        if (startFraction < 1f) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ((1f - startFraction) * BREATHING_TOTAL_MS).toInt(),
                    easing = LinearEasing
                )
            )
        }
    }
    val particles = remember { generateParticles(PARTICLE_COUNT) }
    Canvas(modifier) {
        drawBreathingFrame(progress.value, particles)
    }
}

@Composable
private fun BreathingQuote() {
    Surface(color = BreathingQuoteBg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "“",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = BreathingQuoteText.copy(alpha = 0.45f)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "此刻，就是最好的开始。", fontSize = 14.sp, color = BreathingQuoteText)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "”",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = BreathingQuoteText.copy(alpha = 0.45f)
            )
        }
    }
}

private data class BreathingParticle(
    val angleRad: Float,
    val radiusFraction: Float,
    val dotRadiusDp: Float,
    val appearDelay: Float,
    val appearDuration: Float,
    val gatherStagger: Float,
    val peakAlpha: Float
)

// 只在首次组合调用一次：角度均匀铺开加抖动，其余属性随机，重组不会让小球跳位。
private fun generateParticles(count: Int): List<BreathingParticle> = List(count) { index ->
    BreathingParticle(
        angleRad = (index * (360f / count) + Random.nextFloat() * 24f - 12f) *
            (PI.toFloat() / 180f),
        radiusFraction = 0.58f + Random.nextFloat() * 0.32f,
        dotRadiusDp = 3.5f + Random.nextFloat() * 3.5f,
        appearDelay = (index % 5) * 0.02f + Random.nextFloat() * 0.03f,
        appearDuration = 0.08f + Random.nextFloat() * 0.06f,
        gatherStagger = Random.nextFloat() * 0.05f,
        peakAlpha = 0.45f + Random.nextFloat() * 0.45f
    )
}

private fun DrawScope.drawBreathingFrame(t: Float, particles: List<BreathingParticle>) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val half = min(size.width, size.height) / 2f
    drawConcentricRings(center, half, t)
    particles.forEach { particle -> drawParticle(particle, t, center, half) }
    drawCenterOrb(center, half, t)
}

// 角落两个超低透明度大圆，复刻设计稿的浅色弧面。
private fun DrawScope.drawBackdropDecorations() {
    drawCircle(
        color = BreathingDeep.copy(alpha = 0.035f),
        radius = size.minDimension * 0.45f,
        center = Offset(size.width * -0.08f, size.height * 0.12f)
    )
    drawCircle(
        color = BreathingDeep.copy(alpha = 0.03f),
        radius = size.minDimension * 0.40f,
        center = Offset(size.width * 1.06f, size.height * 0.94f)
    )
}

private fun DrawScope.drawConcentricRings(center: Offset, half: Float, t: Float) {
    val growth = easeOutCubic(clamp01((t - PHASE_BALL_START) / (PHASE_BALL_END - PHASE_BALL_START)))
    val fade = orbFade(t)
    if (growth <= 0f || fade <= 0f) return
    val pulse = orbPulse(t)
    listOf(0.78f to 0.030f, 0.60f to 0.042f, 0.42f to 0.055f).forEach { (radiusFraction, alpha) ->
        drawCircle(
            color = BreathingOrbCore.copy(alpha = alpha * growth * fade),
            radius = half * radiusFraction * pulse,
            center = center
        )
    }
}

private fun DrawScope.drawParticle(
    particle: BreathingParticle,
    t: Float,
    center: Offset,
    half: Float
) {
    val appear = clamp01((t - particle.appearDelay) / particle.appearDuration)
    if (appear <= 0f) return
    val gather = particleGatherProgress(t, particle.gatherStagger)
    val start = center +
        Offset(cos(particle.angleRad), sin(particle.angleRad)) * (half * particle.radiusFraction)
    val position = start + (center - start) * gather
    val dotRadius = particle.dotRadiusDp.dp.toPx() * (1f - 0.85f * gather)
    if (gather >= 0.999f || dotRadius <= 0.5f) return
    val alpha = particle.peakAlpha * appear
    // 拖尾：沿运动反方向的渐隐线，越接近中心拖得越长。
    if (gather > 0.02f && gather < 0.96f) {
        val direction = (center - start) / (center - start).getDistance()
        val tail = position - direction * (dotRadius * (2f + 3f * gather))
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(BreathingOrbCore.copy(alpha = alpha * 0.5f), Color.Transparent),
                start = tail,
                end = position
            ),
            start = tail,
            end = position,
            strokeWidth = dotRadius * 1.4f,
            cap = StrokeCap.Round
        )
    }
    // 柔光用嵌套低透明度圆模拟，minSdk 26 起全版本一致（不用 blur）。
    drawCircle(BreathingOrbCore.copy(alpha = alpha * 0.10f), dotRadius * 2.6f, position)
    drawCircle(BreathingOrbCore.copy(alpha = alpha * 0.20f), dotRadius * 1.7f, position)
    drawCircle(BreathingOrbCore.copy(alpha = alpha), dotRadius, position)
}

private fun DrawScope.drawCenterOrb(center: Offset, half: Float, t: Float) {
    val growth = easeOutCubic(clamp01((t - PHASE_BALL_START) / (PHASE_BALL_END - PHASE_BALL_START)))
    if (growth <= 0f) return
    val fade = orbFade(t)
    if (fade <= 0f) return
    // 渐隐时轻微放大，做出溶解感。
    val radius = half * 0.20f * growth * orbPulse(t) * (1f + 0.25f * (1f - fade))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BreathingOrbCore.copy(alpha = 0.30f * fade), Color.Transparent),
            center = center,
            radius = radius * 2.6f
        ),
        radius = radius * 2.6f,
        center = center
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BreathingOrbCoreLight.copy(alpha = fade), BreathingOrbCore.copy(alpha = fade)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

@Preview(name = "深呼吸页", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
fun BreathingScreenPreview() {
    BreathingScreen(step = 3)
}
