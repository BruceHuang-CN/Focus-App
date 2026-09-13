package com.example.focus_app.ui.stats

import android.content.Context
import com.example.focus_app.R
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.stats.ActivityPeriod
import com.example.focus_app.domain.stats.DecisionKind

internal fun StatsRange.displayLabel(context: Context): String = context.getString(when (this) {
    StatsRange.TODAY -> R.string.core_today
    StatsRange.WEEK -> R.string.core_week_range
    StatsRange.MONTH -> R.string.core_month_range
})
internal fun ActivityPeriod.displayLabel(context: Context): String = context.getString(when (this) {
    ActivityPeriod.WEEK -> R.string.core_week
    ActivityPeriod.MONTH -> R.string.core_month
    ActivityPeriod.YEAR -> R.string.core_year
})
internal fun DecisionKind.displayLabel(context: Context): String = context.getString(when (this) {
    DecisionKind.RETURN -> R.string.core_return_task
    DecisionKind.INTENTIONAL -> R.string.core_intentional
    DecisionKind.REST -> R.string.core_rest
    DecisionKind.LEGACY -> R.string.core_legacy
})
