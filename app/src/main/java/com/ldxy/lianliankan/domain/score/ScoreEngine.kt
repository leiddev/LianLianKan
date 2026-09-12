package com.ldxy.lianliankan.domain.score

/**
 * 计分与连击规则（SRS FR-8，公式见 SRS 附录 9.2）。
 *
 * ```
 * 单次消除得分 = 基础分(10) × 连击倍率
 * 连击倍率     = min(1 + 0.1 × (连击数 - 1), 2.0)
 * 连击判定     = 连续成功消除，且相邻两次间隔 ≤ 3 秒
 * 连击重置     = 错误选中 / 间隔 > 3 秒 / 使用洗牌道具
 * 关卡总分     = Σ(单次消除得分) + 剩余时间(s) × 2
 * ```
 *
 * ### 为什么用整数而不是浮点
 * 倍率 `1 + 0.1 × (n - 1)` 恒为 0.1 的整数倍，因此可以用「十分之一」为单位的整数精确表示，
 * 从而避开二进制浮点在 `10 × 1.1` 这类运算上产生的尾差。所有对外计算走整数路径，
 * 仅 [multiplierFor] 为展示与文案需要返回 [Double]。
 *
 * 本类不持有时间：所有涉及时间的入口都由调用方传入 `nowMillis`，
 * 因此完全确定、可在 JVM 上直接单测（SRS NFR-3.1）。
 */
object ScoreEngine {

    /** SRS 9.2：基础分。 */
    const val BASE_SCORE: Int = 10

    /** SRS FR-8.2：连击窗口 3 秒。 */
    const val COMBO_WINDOW_MILLIS: Long = 3_000L

    /** 每级连击的倍率增量 0.1，以「十分之一」为单位即 1。 */
    const val COMBO_STEP_TENTHS: Int = 1

    /** SRS 9.2：倍率上限 2.0，以「十分之一」为单位即 20。 */
    const val MAX_MULTIPLIER_TENTHS: Int = 20

    /** SRS FR-8.4：通关时剩余每秒折算 2 分。 */
    const val TIME_BONUS_PER_SECOND: Int = 2

    /**
     * 连击数为 [combo] 时的倍率，用于展示（SRS 9.2）。
     *
     * [combo] 小于 1 时按 1 计。
     */
    fun multiplierFor(combo: Int): Double = multiplierTenths(combo) / 10.0

    /** 倍率的整数表示（十分之一为单位）。 */
    fun multiplierTenths(combo: Int): Int {
        val safeCombo = maxOf(combo, 1)
        return minOf(
            MAX_MULTIPLIER_TENTHS,
            10 + (safeCombo - 1) * COMBO_STEP_TENTHS,
        )
    }

    /** 单次消除的得分。连击为 0（尚未连击）时按第 1 次计，即基础分。 */
    fun scoreFor(combo: Int): Int = BASE_SCORE * multiplierTenths(combo) / 10

    /**
     * 成功消除一对牌后更新得分与连击（SRS FR-8.1 / FR-8.2 / FR-8.3）。
     *
     * 距上次消除 **≤ 3 秒** 则连击 +1，否则视为间隔超时、连击从 1 重新开始。
     */
    fun onEliminated(state: ScoreState, nowMillis: Long): ScoreState {
        val previous = state.lastEliminationAtMillis
        val continues = previous != null && nowMillis - previous <= COMBO_WINDOW_MILLIS
        val combo = if (continues) state.combo + 1 else 1

        return ScoreState(
            points = state.points + scoreFor(combo),
            combo = combo,
            maxCombo = maxOf(state.maxCombo, combo),
            lastEliminationAtMillis = nowMillis,
        )
    }

    /**
     * 连击归零，得分与最高连击保持不变。
     *
     * 触发场景：错误选中（FR-8.3）、使用洗牌道具（FR-8.3 / FR-9.4）。
     * **注意**：使用提示道具**不**触发重置（FR-9.4）。
     */
    fun resetCombo(state: ScoreState): ScoreState =
        if (state.combo == 0) state else state.copy(combo = 0, lastEliminationAtMillis = null)

    /** 当前连击是否仍在有效窗口内，用于 UI 决定是否继续展示连击（SRS FR-8.2）。 */
    fun isComboAlive(state: ScoreState, nowMillis: Long): Boolean {
        val previous = state.lastEliminationAtMillis ?: return false
        return state.combo > 0 && nowMillis - previous <= COMBO_WINDOW_MILLIS
    }

    /** 通关时间奖励 = 剩余秒数 × 2（SRS FR-8.4）。 */
    fun timeBonus(timeLeftSeconds: Int): Int =
        maxOf(timeLeftSeconds, 0) * TIME_BONUS_PER_SECOND

    /** 关卡总分 = 消除得分累计 + 时间奖励（SRS 9.2）。 */
    fun finalScore(state: ScoreState, timeLeftSeconds: Int): Int =
        state.points + timeBonus(timeLeftSeconds)
}
