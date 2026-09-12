package com.ldxy.lianliankan.domain.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计分与连击单测，对应 SRS FR-8 与附录 9.2 的公式。
 *
 * 覆盖开发计划 M4-6 要求的「倍率上限 2.0 的边界」「连续 20 次消除的累计得分」「超时归零」。
 */
class ScoreEngineTest {

    // ============================================================ 倍率与单次得分

    @Test
    fun `倍率按 0-1 递增并在 2-0 处封顶`() {
        val expected = mapOf(
            1 to 1.0,
            2 to 1.1,
            3 to 1.2,
            5 to 1.4,
            10 to 1.9,
            11 to 2.0,
            12 to 2.0,
            20 to 2.0,
            100 to 2.0,
        )
        for ((combo, multiplier) in expected) {
            assertEquals(
                "连击 $combo 的倍率应为 $multiplier",
                multiplier,
                ScoreEngine.multiplierFor(combo),
                1e-9,
            )
        }
    }

    @Test
    fun `连击数小于 1 时按第 1 次计`() {
        assertEquals(ScoreEngine.BASE_SCORE, ScoreEngine.scoreFor(0))
        assertEquals(ScoreEngine.BASE_SCORE, ScoreEngine.scoreFor(-5))
        assertEquals(1.0, ScoreEngine.multiplierFor(0), 1e-9)
    }

    @Test
    fun `单次消除得分与 SRS 9-2 公式逐项一致`() {
        // 单次得分 = 10 × min(1 + 0.1 × (连击数 - 1), 2.0)
        val expected = listOf(
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19, // 连击 1..10
            20, 20, 20, 20, 20, 20, 20, 20, 20, 20, // 连击 11..20，已封顶
        )
        for ((index, score) in expected.withIndex()) {
            val combo = index + 1
            assertEquals("连击 $combo 的单次得分", score, ScoreEngine.scoreFor(combo))
        }
    }

    @Test
    fun `得分恒为整数且与倍率一致`() {
        // 倍率是 0.1 的整数倍，整数路径不应产生任何尾差
        for (combo in 1..200) {
            val tenths = ScoreEngine.multiplierTenths(combo)
            assertEquals(
                "连击 $combo 的整数倍率应与浮点倍率一致",
                tenths / 10.0,
                ScoreEngine.multiplierFor(combo),
                1e-9,
            )
            assertEquals(
                "连击 $combo 的得分应等于 基础分 × 整数倍率 / 10",
                ScoreEngine.BASE_SCORE * tenths / 10,
                ScoreEngine.scoreFor(combo),
            )
            assertTrue("连击 $combo 的倍率不应超过上限", tenths <= ScoreEngine.MAX_MULTIPLIER_TENTHS)
        }
    }

    // ============================================================ 连击窗口

    @Test
    fun `首次消除形成连击 1 并获得基础分`() {
        val after = ScoreEngine.onEliminated(ScoreState(), nowMillis = 1_000L)

        assertEquals(1, after.combo)
        assertEquals(ScoreEngine.BASE_SCORE, after.points)
        assertEquals(1, after.maxCombo)
        assertEquals(1_000L, after.lastEliminationAtMillis)
    }

    @Test
    fun `窗口内连续消除连击递增且得分递增`() {
        var state = ScoreState()
        state = ScoreEngine.onEliminated(state, nowMillis = 0L)
        state = ScoreEngine.onEliminated(state, nowMillis = 1_000L)
        state = ScoreEngine.onEliminated(state, nowMillis = 2_000L)

        assertEquals(3, state.combo)
        assertEquals(10 + 11 + 12, state.points)
        assertEquals(3, state.maxCombo)
    }

    @Test
    fun `间隔恰好等于 3 秒仍算连击`() {
        // SRS FR-8.2：相邻两次间隔 ≤ 3 秒即形成连击
        var state = ScoreEngine.onEliminated(ScoreState(), nowMillis = 0L)
        state = ScoreEngine.onEliminated(state, nowMillis = ScoreEngine.COMBO_WINDOW_MILLIS)

        assertEquals("间隔正好 3 秒应仍连击", 2, state.combo)
    }

    @Test
    fun `间隔超过 3 秒连击归零重新计数`() {
        var state = ScoreEngine.onEliminated(ScoreState(), nowMillis = 0L)
        state = ScoreEngine.onEliminated(state, nowMillis = 1_000L)
        assertEquals(2, state.combo)

        val timedOut = ScoreEngine.onEliminated(
            state,
            nowMillis = 1_000L + ScoreEngine.COMBO_WINDOW_MILLIS + 1L,
        )
        assertEquals("超时后连击应从 1 重新开始", 1, timedOut.combo)
        assertEquals("超时后本次只得基础分", ScoreEngine.BASE_SCORE, timedOut.points - state.points)
        assertEquals("最高连击应保留", 2, timedOut.maxCombo)
    }

    @Test
    fun `连续 20 次消除的累计得分为 345`() {
        // 连击 1..11 单次得分为 10..20（合计 165），连击 12..20 每次封顶 20（合计 180）
        var state = ScoreState()
        for (round in 0 until 20) {
            state = ScoreEngine.onEliminated(state, nowMillis = round * 1_000L)
        }

        assertEquals(20, state.combo)
        assertEquals(20, state.maxCombo)
        assertEquals(165 + 180, state.points)
    }

    @Test
    fun `isComboAlive 反映连击是否仍在窗口内`() {
        val state = ScoreEngine.onEliminated(ScoreState(), nowMillis = 0L)

        assertTrue(ScoreEngine.isComboAlive(state, nowMillis = 0L))
        assertTrue(ScoreEngine.isComboAlive(state, nowMillis = ScoreEngine.COMBO_WINDOW_MILLIS))
        assertFalse(
            ScoreEngine.isComboAlive(state, nowMillis = ScoreEngine.COMBO_WINDOW_MILLIS + 1L),
        )
        assertFalse("未消除过时不应存在连击", ScoreEngine.isComboAlive(ScoreState(), 0L))
    }

    // ============================================================ 连击重置

    @Test
    fun `resetCombo 清零连击但保留得分与最高连击`() {
        var state = ScoreState()
        state = ScoreEngine.onEliminated(state, nowMillis = 0L)
        state = ScoreEngine.onEliminated(state, nowMillis = 500L)
        val pointsBefore = state.points

        val reset = ScoreEngine.resetCombo(state)

        assertEquals(0, reset.combo)
        assertEquals(pointsBefore, reset.points)
        assertEquals(2, reset.maxCombo)
        assertEquals(null, reset.lastEliminationAtMillis)
    }

    @Test
    fun `重置后下一次消除从连击 1 重新开始`() {
        var state = ScoreState()
        state = ScoreEngine.onEliminated(state, nowMillis = 0L)
        state = ScoreEngine.onEliminated(state, nowMillis = 100L)
        state = ScoreEngine.resetCombo(state)
        state = ScoreEngine.onEliminated(state, nowMillis = 200L)

        assertEquals(1, state.combo)
    }

    @Test
    fun `连击已为 0 时重置返回自身`() {
        val state = ScoreState(points = 50, combo = 0, maxCombo = 3)
        assertEquals(state, ScoreEngine.resetCombo(state))
    }

    // ============================================================ 时间奖励与总分

    @Test
    fun `时间奖励为剩余秒数乘以 2`() {
        assertEquals(0, ScoreEngine.timeBonus(0))
        assertEquals(2, ScoreEngine.timeBonus(1))
        assertEquals(300, ScoreEngine.timeBonus(150))
        assertEquals("负数剩余时间不应产生负分", 0, ScoreEngine.timeBonus(-10))
    }

    @Test
    fun `关卡总分为消除得分加时间奖励`() {
        var state = ScoreState()
        state = ScoreEngine.onEliminated(state, nowMillis = 0L)
        state = ScoreEngine.onEliminated(state, nowMillis = 500L)

        assertEquals(10 + 11, state.points)
        assertEquals(10 + 11 + 2 * 100, ScoreEngine.finalScore(state, timeLeftSeconds = 100))
    }

    @Test
    fun `未消除任何牌时总分为纯时间奖励`() {
        assertEquals(360, ScoreEngine.finalScore(ScoreState(), timeLeftSeconds = 180))
    }
}
