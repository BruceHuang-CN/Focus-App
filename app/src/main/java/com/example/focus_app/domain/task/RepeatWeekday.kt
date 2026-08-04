package com.example.focus_app.domain.task

/**
 * 重复星期掩码工具。位 0=周一 … 位 6=周日，与 ActiveTaskResolver 的 dayMask 保持一致。
 */
object RepeatWeekday {
    const val MONDAY = 1 shl 0
    const val TUESDAY = 1 shl 1
    const val WEDNESDAY = 1 shl 2
    const val THURSDAY = 1 shl 3
    const val FRIDAY = 1 shl 4
    const val SATURDAY = 1 shl 5
    const val SUNDAY = 1 shl 6

    const val ALL = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY or SATURDAY or SUNDAY

    val ORDER = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun has(mask: Int, index: Int): Boolean = mask and (1 shl index) != 0

    fun toggle(mask: Int, index: Int): Int = mask xor (1 shl index)

    fun label(mask: Int): String {
        if (mask and ALL == ALL) return "每天"
        if (mask == 0) return "未设置"
        return ORDER.filterIndexed { index, _ -> has(mask, index) }.joinToString("、")
    }
}
