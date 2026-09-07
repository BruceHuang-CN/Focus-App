package com.example.focus_app.ui.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 只测时间轴纯函数的数学性质；视觉效果由真机/预览验收。
class BreathingTimelineTest {

    @Test
    fun clamp01_bounds() {
        assertEquals(0f, clamp01(-0.5f), 0f)
        assertEquals(0.25f, clamp01(0.25f), 0f)
        assertEquals(1f, clamp01(1.5f), 0f)
    }

    @Test
    fun easeInOutCubic_endpoints() {
        assertEquals(0f, easeInOutCubic(0f), 0f)
        assertEquals(1f, easeInOutCubic(1f), 0f)
    }

    @Test
    fun easeInOutCubic_monotonic() {
        var previous = easeInOutCubic(0f)
        for (i in 1..20) {
            val value = easeInOutCubic(i / 20f)
            assertTrue("easeInOutCubic 应单调不减，$i/20 处 $value < $previous", value >= previous)
            previous = value
        }
    }

    @Test
    fun gatherStaggerNeverExceedsSpan() {
        // 分母 (跨度 - stagger) 恒正，杜绝除零/负分母导致 NaN。
        assertTrue(
            "gatherStagger($PARTICLE_MAX_GATHER_STAGGER) 不得 ≥ 聚集跨度",
            PARTICLE_MAX_GATHER_STAGGER < (PHASE_GATHER_END - PHASE_GATHER_START)
        )
    }

    @Test
    fun gatherProgress_atSpanEnd_allParticlesArrive() {
        // 无论 stagger 取多少，在聚集阶段结束时全部小球都到达中心（进度 1）。
        for (stagger in listOf(0f, PARTICLE_MAX_GATHER_STAGGER / 2f, PARTICLE_MAX_GATHER_STAGGER)) {
            assertEquals(1f, particleGatherProgress(PHASE_GATHER_END, stagger), 0f)
        }
    }

    @Test
    fun gatherProgress_zeroBeforeStart() {
        // 聚集尚未开始时（stagger=0）进度为 0；小球停在起点。
        assertEquals(0f, particleGatherProgress(PHASE_GATHER_START - 0.01f, 0f), 0f)
    }

    @Test
    fun gatherProgress_monotonic() {
        var previous = particleGatherProgress(PHASE_GATHER_START, 0f)
        for (i in 1..32) {
            val t = PHASE_GATHER_START + (PHASE_GATHER_END - PHASE_GATHER_START) * i / 32f
            val value = particleGatherProgress(t, 0f)
            assertTrue("聚集进度应单调不减，t=$t 处 $value < $previous", value >= previous)
            previous = value
        }
    }

    @Test
    fun orbPulse_isOneAtPhaseBoundaries() {
        // 成球瞬间与开始渐隐瞬间脉动相位为 0，动画衔接不跳变。
        assertEquals(1f, orbPulse(PHASE_BALL_END), 1e-3f)
        assertEquals(1f, orbPulse(PHASE_FADE_START), 1e-3f)
    }

    @Test
    fun orbFade_fullBeforeFadeStart_zeroAtEnd() {
        assertEquals(1f, orbFade(PHASE_FADE_START - 0.01f), 0f)
        assertEquals(0f, orbFade(PHASE_FADE_END), 0f)
    }
}
