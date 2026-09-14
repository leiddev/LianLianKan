package com.ldxy.lianliankan.ui

import com.ldxy.lianliankan.data.SettingsRepository
import com.ldxy.lianliankan.domain.model.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 启动画面的放行门控（V1.4 需求 ②）。
 *
 * ### 为什么要单独一个对象
 * 这段逻辑承载了整次改进里**唯一一条高风险项**（V1.4 计划 R-3）：
 * 启动画面「按住不放」的条件一旦永不为真，应用就再也起不来。
 * 放在 `MainActivity` 里意味着它只能靠真机验收，写不出一条断言；
 * 提出来就是一个纯挂起函数，能用 `runTest` 的虚拟时间把「正常路径」与
 * 「超时兜底」两条分支都钉死。
 *
 * 它不做任何调度、不持有状态，调用方（`MainActivity`）只负责把结果写回自己的字段。
 */
object SplashGate {

    /**
     * 兜底超时（毫秒）。
     *
     * 取值依据：本地 DataStore 的正常读盘在几十毫秒内完成，1.5s 既不会误伤正常路径，
     * 又能在读盘异常时足够快地放行 —— 玩家宁可先看到默认配色，也不能卡在启动画面。
     */
    const val TIMEOUT_MS = 1_500L

    /**
     * 等设置就绪，并返回**首个真实设置**。
     *
     * @return 正常路径下是 DataStore 里的真实设置；超时兜底路径下是 `null`
     *   （调用方应退回 [Settings] 的默认值，并照常放行）。
     *
     * 两件事共用**同一个**超时：到点后协程被取消，等信号与取数据一起作废。
     * 注意这不是「用 delay 猜时间」—— 正常路径完全由 [SettingsRepository.isLoaded]
     * 这个数据信号驱动，超时只在读盘异常时生效。
     */
    suspend fun awaitSettings(
        repository: SettingsRepository,
        timeoutMs: Long = TIMEOUT_MS,
    ): Settings? = withTimeoutOrNull(timeoutMs) {
        // ① 先等「已读到首个真实值」：短了没用（界面会先按默认配色渲染一帧），
        //    长了则平白拖慢启动，因此只能由数据本身来报信。
        repository.isLoaded.first { it }
        // ② 再把那个值取回来：让 setContent 的**首次组合**就能用上玩家选的配色，
        //    而不是先组合默认配色、等值到了再重组跳色（那正是需求 ② 里的第二处闪烁）。
        //    此刻 DataStore 的值已在内存缓存里，这一步不会真的等 IO。
        repository.settings.first()
    }
}
