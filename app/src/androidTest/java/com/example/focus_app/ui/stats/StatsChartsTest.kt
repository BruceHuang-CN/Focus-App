package com.example.focus_app.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focus_app.domain.model.AppShare
import com.example.focus_app.domain.model.DayBucket
import com.example.focus_app.domain.model.FocusStats
import com.example.focus_app.domain.model.HourBucket
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StatsChartsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun charts_render_with_sample_data() {
        composeRule.setContent {
            MaterialTheme {
                HourlyBarChart(sampleStats.hourly)
                DailyTrendChart(sampleStats.daily)
                AppDonutChart(sampleStats.byApp)
            }
        }

        composeRule.onNodeWithTag("hourly_bar_chart").assertExists()
        composeRule.onNodeWithTag("daily_trend_chart").assertExists()
        composeRule.onNodeWithTag("app_donut_chart").assertExists()
    }

    @Test
    fun charts_show_empty_hint_without_data() {
        composeRule.setContent {
            MaterialTheme {
                HourlyBarChart(emptyList())
            }
        }
        composeRule.onNodeWithText("暂无数据").assertExists()
    }

    private val sampleStats = FocusStats(
        totalDurationMinutes = 60,
        openCount = 2,
        remindedCount = 1,
        activeExitCount = 1,
        continuedCount = 1,
        exitRate = 0.5f,
        hourly = (0..23).map { HourBucket(it, if (it == 10) 30 else 0, if (it == 10) 1 else 0) },
        daily = listOf(
            DayBucket(LocalDate.of(2026, 8, 3), 30, 1),
            DayBucket(LocalDate.of(2026, 8, 4), 60, 2)
        ),
        byApp = listOf(
            AppShare("com.a", "抖音", 40),
            AppShare("com.b", "B站", 20)
        )
    )
}
