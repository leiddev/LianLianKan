package com.ldxy.lianliankan.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反馈层单测（开发计划 M9）。
 *
 * 真正调用系统振动器与 `SoundPool` 的代码无法在 JVM 上覆盖，但**两件会写错的事可以**：
 * 1. FR-12.2 要求五类事件的振动「幅度与时长区分」—— 波形参数是纯数据，可以逐项断言；
 * 2. FR-12.3 要求关闭音效/振动后不再触发 —— 用假播放器即可验证开关确实生效。
 */
class FeedbackDispatcherTest {

    private class RecordingSoundPlayer : SoundPlayer {
        val played = mutableListOf<FeedbackEvent>()
        var released = false
        override fun play(event: FeedbackEvent) {
            played += event
        }

        override fun release() {
            released = true
        }
    }

    private class RecordingVibratorPlayer : VibratorPlayer {
        val vibrated = mutableListOf<FeedbackEvent>()
        override fun vibrate(event: FeedbackEvent) {
            vibrated += event
        }
    }

    // ============================================================ 开关（FR-12.3）

    @Test
    fun `两个开关都开启时音效与振动都会触发`() {
        val sound = RecordingSoundPlayer()
        val vibrator = RecordingVibratorPlayer()
        val dispatcher = FeedbackDispatcher(sound, vibrator, { true }, { true })

        dispatcher.dispatch(FeedbackEvent.ELIMINATE)

        assertEquals(listOf(FeedbackEvent.ELIMINATE), sound.played)
        assertEquals(listOf(FeedbackEvent.ELIMINATE), vibrator.vibrated)
    }

    @Test
    fun `关闭音效后不再播放音效但振动仍触发`() {
        val sound = RecordingSoundPlayer()
        val vibrator = RecordingVibratorPlayer()
        val dispatcher = FeedbackDispatcher(sound, vibrator, { false }, { true })

        dispatcher.dispatch(FeedbackEvent.SELECT)

        assertTrue("关闭音效后不应播放任何音效", sound.played.isEmpty())
        assertEquals(listOf(FeedbackEvent.SELECT), vibrator.vibrated)
    }

    @Test
    fun `关闭振动后不再触发振动但音效仍播放`() {
        val sound = RecordingSoundPlayer()
        val vibrator = RecordingVibratorPlayer()
        val dispatcher = FeedbackDispatcher(sound, vibrator, { true }, { false })

        dispatcher.dispatch(FeedbackEvent.ERROR)

        assertEquals(listOf(FeedbackEvent.ERROR), sound.played)
        assertTrue("关闭振动后不应触发任何振动", vibrator.vibrated.isEmpty())
    }

    @Test
    fun `开关是每次读取而非构造时快照 - 中途关闭立即生效`() {
        val sound = RecordingSoundPlayer()
        val vibrator = RecordingVibratorPlayer()
        var soundOn = true
        val dispatcher = FeedbackDispatcher(sound, vibrator, { soundOn }, { true })

        dispatcher.dispatch(FeedbackEvent.SELECT)
        soundOn = false
        dispatcher.dispatch(FeedbackEvent.ELIMINATE)

        assertEquals(
            "设置里关掉音效后应立刻生效（FR-11.6 / FR-12.3）",
            listOf(FeedbackEvent.SELECT),
            sound.played,
        )
    }

    @Test
    fun `release 会释放音效资源`() {
        val sound = RecordingSoundPlayer()
        FeedbackDispatcher(sound, RecordingVibratorPlayer(), { true }, { true }).release()

        assertTrue("界面销毁时应释放 SoundPool", sound.released)
    }

    // ============================================================ 波形（FR-12.2）

    @Test
    fun `五类事件都有对应的振动波形且长度自洽`() {
        for (event in FeedbackEvent.entries) {
            val timings = FeedbackSpec.timingsFor(event)
            val amplitudes = FeedbackSpec.amplitudesFor(event)

            assertTrue("$event 的 timings 不应为空", timings.isNotEmpty())
            assertEquals(
                "$event 的 amplitudes 必须与 timings 等长，否则 createWaveform 会抛异常",
                timings.size,
                amplitudes.size,
            )
            assertEquals(
                "$event 的 timings[0] 是起始等待时长，应为 0",
                0L,
                timings[0],
            )
            assertTrue(
                "$event 的振幅必须落在 0..255",
                amplitudes.all { it in 0..255 },
            )
            assertTrue(
                "$event 的振动段时长应为正数",
                timings.drop(1).all { it > 0 },
            )
        }
    }

    @Test
    fun `五类事件的振动总时长互不相同 - 满足幅度与时长区分`() {
        val durations = FeedbackEvent.entries.associateWith { FeedbackSpec.timingsFor(it).sum() }

        assertEquals(
            "五个事件的振动总时长应两两不同（FR-12.2）",
            FeedbackEvent.entries.size,
            durations.values.toSet().size,
        )
    }

    @Test
    fun `五类事件的波形互不相同`() {
        val shapes = FeedbackEvent.entries.map { event ->
            FeedbackSpec.timingsFor(event).toList() to FeedbackSpec.amplitudesFor(event).toList()
        }

        assertEquals("五个事件的波形应两两不同", FeedbackEvent.entries.size, shapes.toSet().size)
        assertFalse("不应有空波形", shapes.any { it.first.isEmpty() })
    }

    @Test
    fun `错误事件是双击式 - 有两段振动`() {
        val amplitudes = FeedbackSpec.amplitudesFor(FeedbackEvent.ERROR)
        assertEquals("错误反馈应是两段振动，给出明确的否定感", 2, amplitudes.count { it > 0 })
    }

    @Test
    fun `胜利事件的振动强度递增`() {
        val amplitudes = FeedbackSpec.amplitudesFor(FeedbackEvent.WIN).filter { it > 0 }
        assertEquals("胜利应有三次振动", 3, amplitudes.size)
        assertEquals(
            "胜利的振动强度应逐段递增",
            amplitudes.sorted(),
            amplitudes,
        )
    }
}
