package com.ldxy.lianliankan.feedback

/**
 * 需要音效与振动反馈的游戏事件（SRS FR-12.1）。
 *
 * 五类与 SRS FR-12.1 一一对应：选中 / 消除 / 错误 / 通关 / 失败。
 */
enum class FeedbackEvent {
    /** 选中一张牌（SRS FR-4.1）。 */
    SELECT,

    /** 消除成功（SRS FR-4.3）。 */
    ELIMINATE,

    /** 错误选中或无法连通（SRS FR-4.4 / FR-4.5）。 */
    ERROR,

    /** 通关（SRS FR-10.2）。 */
    WIN,

    /** 时间耗尽（SRS FR-10.3）。 */
    LOSE,
}

/**
 * 各事件的振动波形参数（SRS FR-12.2：幅度与时长区分）。
 *
 * 抽成纯数据对象是为了能在 JVM 上直接断言「五个事件的振动确实各不相同」——
 * 这是 FR-12.2 的可验证部分，而真正调用系统振动器的那几行无法在单测里覆盖。
 *
 * 波形遵循 Android `VibrationEffect.createWaveform(timings, amplitudes, repeat)` 的约定：
 * `timings[0]` 是开始前的等待时长，其后在「振动 / 停顿」之间交替；
 * `amplitudes` 与 `timings` 等长，取值 0..255，0 表示该段停顿。
 */
internal object FeedbackSpec {

    fun timingsFor(event: FeedbackEvent): LongArray = when (event) {
        // 轻短：每次选牌都响，不能拖沓
        FeedbackEvent.SELECT -> longArrayOf(0, 15)
        // 干脆的一下
        FeedbackEvent.ELIMINATE -> longArrayOf(0, 20)
        // 双击式：明确的「不行」
        FeedbackEvent.ERROR -> longArrayOf(0, 25, 55, 25)
        // 三段上行，庆祝感
        FeedbackEvent.WIN -> longArrayOf(0, 35, 55, 35, 55, 70)
        // 单次长振，低沉
        FeedbackEvent.LOSE -> longArrayOf(0, 220)
    }

    fun amplitudesFor(event: FeedbackEvent): IntArray = when (event) {
        FeedbackEvent.SELECT -> intArrayOf(0, 60)
        FeedbackEvent.ELIMINATE -> intArrayOf(0, 120)
        FeedbackEvent.ERROR -> intArrayOf(0, 180, 0, 180)
        FeedbackEvent.WIN -> intArrayOf(0, 130, 0, 160, 0, 210)
        FeedbackEvent.LOSE -> intArrayOf(0, 90)
    }
}
