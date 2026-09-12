package com.ldxy.lianliankan.domain.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 倒计时单测，对应 SRS FR-7。
 *
 * 覆盖开发计划 M4-6 要求的「暂停期间计时不推进」与「归零触发失败」。
 * 全部用例注入假时钟，因此完全确定、无 sleep。
 */
class GameTimerTest {

    /** 可推进的假时钟（毫秒）。 */
    private class FakeClock {
        var now: Long = 0L
            private set

        fun advance(millis: Long) {
            now += millis
        }

        fun asClock(): () -> Long = { now }
    }

    private fun timer(limitSeconds: Int, clock: FakeClock) =
        GameTimer(limitSeconds, clock.asClock())

    // ============================================================ 基本推进

    @Test
    fun `未开始计时时时间不流逝`() {
        val clock = FakeClock()
        val timer = timer(180, clock)

        clock.advance(5_000)
        assertEquals(0L, timer.elapsedMillis())
        assertEquals(180, timer.remainingSeconds())
        assertFalse(timer.isRunning)
    }

    @Test
    fun `开始计时后剩余秒数随推进递减`() {
        val clock = FakeClock()
        val timer = timer(180, clock)
        timer.start()

        assertEquals(180, timer.remainingSeconds())

        clock.advance(1_000)
        assertEquals(179, timer.remainingSeconds())

        // 不足 1 秒仍显示 179（向上取整，避免刚过 1 秒就跳 2）
        clock.advance(500)
        assertEquals(179, timer.remainingSeconds())

        clock.advance(500)
        assertEquals(178, timer.remainingSeconds())

        clock.advance(178_000)
        assertEquals(0, timer.remainingSeconds())
    }

    @Test
    fun `剩余时间向上取整`() {
        val clock = FakeClock()
        val timer = timer(10, clock)
        timer.start()

        clock.advance(1)
        assertEquals("刚开始不应立刻掉 1 秒", 10, timer.remainingSeconds())
        clock.advance(999)
        assertEquals(9, timer.remainingSeconds())
    }

    // ============================================================ 暂停与继续（FR-7.3）

    @Test
    fun `暂停期间计时不推进`() {
        val clock = FakeClock()
        val timer = timer(180, clock)
        timer.start()

        clock.advance(10_000)
        assertEquals(170, timer.remainingSeconds())

        timer.pause()
        assertFalse(timer.isRunning)

        clock.advance(60_000)
        assertEquals("暂停期间不应流逝", 10_000L, timer.elapsedMillis())
        assertEquals("暂停期间剩余时间不应变化", 170, timer.remainingSeconds())
    }

    @Test
    fun `继续后从暂停处接着走`() {
        val clock = FakeClock()
        val timer = timer(180, clock)
        timer.start()

        clock.advance(10_000)
        timer.pause()
        clock.advance(60_000)
        timer.resume()

        assertTrue(timer.isRunning)
        assertEquals(10_000L, timer.elapsedMillis())

        clock.advance(5_000)
        assertEquals("继续后应在原有基础上累加", 15_000L, timer.elapsedMillis())
        assertEquals(165, timer.remainingSeconds())
    }

    @Test
    fun `重复暂停或继续无副作用`() {
        val clock = FakeClock()
        val timer = timer(180, clock)
        timer.start()
        clock.advance(1_000)

        timer.pause()
        timer.pause()
        assertEquals(1_000L, timer.elapsedMillis())

        timer.resume()
        timer.resume()
        clock.advance(1_000)
        assertEquals("重复 resume 不应重置计时区间", 2_000L, timer.elapsedMillis())
    }

    @Test
    fun `未开始时调用暂停是空操作`() {
        val clock = FakeClock()
        val timer = timer(180, clock)

        timer.pause()
        clock.advance(1_000)
        assertEquals(0L, timer.elapsedMillis())
        assertFalse(timer.isRunning)
    }

    @Test
    fun `未开始时调用继续等同于开始`() {
        val clock = FakeClock()
        val timer = timer(180, clock)

        timer.resume()
        assertTrue(timer.isRunning)
        clock.advance(1_000)
        assertEquals(1_000L, timer.elapsedMillis())
    }

    // ============================================================ 归零（FR-7.2）

    @Test
    fun `时间耗尽后 isExpired 为真且剩余为 0`() {
        val clock = FakeClock()
        val timer = timer(3, clock)
        timer.start()

        clock.advance(2_999)
        assertFalse("尚差 1 毫秒时不应判定超时", timer.isExpired)
        assertEquals(1, timer.remainingSeconds())

        clock.advance(1)
        assertTrue(timer.isExpired)
        assertEquals(0, timer.remainingSeconds())
        assertEquals(0L, timer.remainingMillis())
    }

    @Test
    fun `超出限时后剩余时间不出现负数`() {
        val clock = FakeClock()
        val timer = timer(3, clock)
        timer.start()

        clock.advance(60_000)
        assertTrue(timer.isExpired)
        assertEquals(0, timer.remainingSeconds())
        assertEquals(0L, timer.remainingMillis())
    }

    @Test
    fun `暂停可以把超时的推进挡在门外`() {
        val clock = FakeClock()
        val timer = timer(3, clock)
        timer.start()

        clock.advance(2_000)
        timer.pause()
        clock.advance(600_000)

        assertFalse("暂停期间不应因真实时间流逝而超时", timer.isExpired)
        assertEquals(1, timer.remainingSeconds())
    }

    // ============================================================ 低时间告警（FR-7.4）

    @Test
    fun `剩余时间低于 30 秒时进入告警`() {
        val clock = FakeClock()
        val timer = timer(180, clock)
        timer.start()

        clock.advance(150_000)
        assertEquals(30, timer.remainingSeconds())
        assertFalse("恰好 30 秒不算低于 30", timer.isTimeLow)

        clock.advance(1_000)
        assertEquals(29, timer.remainingSeconds())
        assertTrue(timer.isTimeLow)
    }

    @Test
    fun `告警阈值常量与需求一致`() {
        assertEquals(30, GameTimer.LOW_TIME_THRESHOLD_SECONDS)
    }

    // ============================================================ 其他

    @Test
    fun `start 会清零已流逝时间`() {
        val clock = FakeClock()
        val timer = timer(180, clock)
        timer.start()
        clock.advance(5_000)
        assertEquals(5_000L, timer.elapsedMillis())

        timer.start()
        assertEquals("重开本关应清零", 0L, timer.elapsedMillis())
        assertEquals(180, timer.remainingSeconds())
    }

    @Test
    fun `限时非正时构造失败`() {
        try {
            GameTimer(0, FakeClock().asClock())
            throw AssertionError("限时为 0 应当被拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("0"))
        }
    }

    @Test
    fun `默认使用单调时钟且初始剩余等于限时`() {
        // 不注入时钟，验证默认路径可用（SRS 5.2：单调时钟）
        val timer = GameTimer(180)
        timer.start()
        assertEquals(180, timer.remainingSeconds())
        assertFalse(timer.isExpired)
        assertEquals(180_000L, timer.limitMillis)
    }
}
