package com.ldxy.lianliankan.domain.score

/**
 * 计分与连击的当前状态（对应 SRS 7.1 中的 `ScoreState`）。
 *
 * 不可变；所有变更由 [ScoreEngine] 的纯函数返回新实例。
 *
 * @param points 当前累计得分。
 * @param combo 当前连击数。0 表示尚无连击（未消除过，或被 [ScoreEngine.resetCombo] 清零）。
 * @param maxCombo 本局出现过的最高连击数，用于结算面板展示（SRS FR-10.4）。
 * @param lastEliminationAtMillis 上一次成功消除的时间戳（毫秒），用于判定 3 秒连击窗口；
 *   `null` 表示本局尚未消除过。
 */
data class ScoreState(
    val points: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val lastEliminationAtMillis: Long? = null,
)
