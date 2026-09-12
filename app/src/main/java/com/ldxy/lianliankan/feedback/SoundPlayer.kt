package com.ldxy.lianliankan.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes

/**
 * 播放短音效。
 *
 * 抽成接口是为了让 [FeedbackDispatcher] 的「开关是否生效」（SRS FR-12.3）
 * 能在 JVM 上用假实现直接单测。
 */
interface SoundPlayer {

    fun play(event: FeedbackEvent)

    /** 释放底层资源；界面销毁时调用。 */
    fun release()
}

/**
 * 基于 [SoundPool] 的音效播放器（SRS 5.2 软件/硬件接口）。
 *
 * **不包含背景音乐**（SRS FR-12.4）—— 本类只播放与操作一一对应的短音效。
 *
 * 音效素材为自制正弦波合成（`tools/generate-sfx.ps1`），无版权风险（SRS NFR-7.2）。
 */
class AndroidSoundPlayer(context: Context) : SoundPlayer {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // 游戏音效：跟随媒体音量、可被系统按「游戏」类别调节
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundIds: Map<FeedbackEvent, Int> =
        FeedbackEvent.entries.associateWith { event ->
            soundPool.load(context, resourceFor(event), 1)
        }

    override fun play(event: FeedbackEvent) {
        val soundId = soundIds[event] ?: return
        // load 是异步的：首次播放时可能还没解码完，SoundPool 会自行忽略，不会崩
        soundPool.play(soundId, VOLUME, VOLUME, PRIORITY, NO_LOOP, NORMAL_RATE)
    }

    override fun release() {
        runCatching { soundPool.release() }
            .onFailure { Log.w(TAG, "释放 SoundPool 失败", it) }
    }

    @RawRes
    private fun resourceFor(event: FeedbackEvent): Int = when (event) {
        FeedbackEvent.SELECT -> com.ldxy.lianliankan.R.raw.sfx_select
        FeedbackEvent.ELIMINATE -> com.ldxy.lianliankan.R.raw.sfx_eliminate
        FeedbackEvent.ERROR -> com.ldxy.lianliankan.R.raw.sfx_error
        FeedbackEvent.WIN -> com.ldxy.lianliankan.R.raw.sfx_win
        FeedbackEvent.LOSE -> com.ldxy.lianliankan.R.raw.sfx_lose
    }

    private companion object {
        const val TAG = "SoundPlayer"
        const val MAX_STREAMS = 4
        const val VOLUME = 0.7f
        const val PRIORITY = 1
        const val NO_LOOP = 0
        const val NORMAL_RATE = 1.0f
    }
}
