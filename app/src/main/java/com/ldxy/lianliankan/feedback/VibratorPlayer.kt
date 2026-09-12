package com.ldxy.lianliankan.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/** 触发振动反馈；不支持或未授权时静默降级（SRS 2.6）。 */
interface VibratorPlayer {

    fun vibrate(event: FeedbackEvent)
}

/**
 * 系统振动器实现（SRS FR-12.2）。
 *
 * 按 API 分支适配：
 * - API 31+ 走 `VibratorManager.defaultVibrator`（`Vibrator` 的旧用法在该版本起被弃用）；
 * - API 26+ 用 `VibrationEffect.createWaveform(timings, amplitudes, repeat)`，才能表达
 *   FR-12.2 要求的「幅度区分」；
 * - API 24–25 退化为只有时长区分的 `vibrate(timings, repeat)`。
 *
 * 设备没有振动马达、或用户关闭了系统振动时一律静默返回（SRS 2.6 的降级要求）。
 */
class AndroidVibratorPlayer(context: Context) : VibratorPlayer {

    private val vibrator: Vibrator? = resolveVibrator(context)

    private val canVibrate: Boolean = vibrator?.hasVibrator() == true

    override fun vibrate(event: FeedbackEvent) {
        val target = vibrator ?: return
        if (!canVibrate) return

        val timings = FeedbackSpec.timingsFor(event)
        val amplitudes = FeedbackSpec.amplitudesFor(event)

        // 振动只是锦上添花：任何失败都不应影响游戏进行，因此整体兜住异常
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                target.vibrate(VibrationEffect.createWaveform(timings, amplitudes, REPEAT_NONE))
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(timings, REPEAT_NONE)
            }
        }.onFailure { Log.w(TAG, "振动反馈失败，已忽略", it) }
    }

    private companion object {
        const val TAG = "VibratorPlayer"
        const val REPEAT_NONE = -1

        fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
    }
}
