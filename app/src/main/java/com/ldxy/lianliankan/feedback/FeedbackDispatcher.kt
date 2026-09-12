package com.ldxy.lianliankan.feedback

/**
 * 反馈分发：把游戏事件转成音效与振动，并统一执行两个开关（SRS FR-12.3）。
 *
 * 开关状态通过 lambda 传入而不是构造时快照 —— 玩家在设置里关掉音效后应当**立刻**生效
 * （SRS FR-11.6），若在构造时读一次就会用到过期值。
 *
 * 本类不含任何判断游戏规则的逻辑，只做两件事：读开关、按开关转发。
 */
class FeedbackDispatcher(
    private val soundPlayer: SoundPlayer,
    private val vibratorPlayer: VibratorPlayer,
    private val soundEnabled: () -> Boolean,
    private val vibrationEnabled: () -> Boolean,
) {

    fun dispatch(event: FeedbackEvent) {
        if (soundEnabled()) soundPlayer.play(event)
        if (vibrationEnabled()) vibratorPlayer.vibrate(event)
    }

    /** 释放音效资源；界面销毁时调用。 */
    fun release() {
        soundPlayer.release()
    }
}
