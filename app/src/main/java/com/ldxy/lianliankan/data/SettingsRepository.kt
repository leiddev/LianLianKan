package com.ldxy.lianliankan.data

import com.ldxy.lianliankan.domain.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 用户设置仓库（SRS FR-11.6 / FR-14.1、数据字典见 SRS 7.2）。
 *
 * M7 只依赖这个接口，M8 会用 DataStore 实现替换 [InMemorySettingsRepository]，
 * 因此界面层无需改动（SRS NFR-3.1 的分层意图）。
 */
interface SettingsRepository {

    /**
     * 当前设置，随修改实时更新（SRS FR-11.6：修改后立即生效）。
     *
     * 暴露 [Flow] 而非 `StateFlow`：DataStore 实现拿不到协程作用域、无法自行转成 StateFlow。
     * 需要同步初值的调用方（如 `MainActivity`）自行通过 `collectAsStateWithLifecycle(initialValue)` 提供。
     */
    val settings: Flow<Settings>

    /**
     * 是否已读到设置的**首个真实值**（V1.4 需求 ②）。
     *
     * ### 为什么需要这个信号
     * 冷启动时启动画面要「按住」到设置就绪再放行。若不等，界面会先按 [Settings] 的
     * 默认值（主题编号 0）渲染一帧，读到 DataStore 的真实值后再跳到玩家实际选的
     * 配色 —— 这是一次肉眼可见的颜色跳变，与白屏叠加在一起，正是需求 ② 要解决的。
     *
     * 界面层**不能靠 delay 猜时间**（猜短了没用、猜长了平白拖慢启动），
     * 所以由仓库层给出一个明确信号：[DataStoreSettingsRepository] 在首个值读到的
     * 瞬间置 true，[InMemorySettingsRepository] 因为本就在内存里，恒为 true。
     *
     * 注意它只表示「读到了」，不表示「读成功」—— 读盘失败时它会一直停在 false，
     * 由调用方的超时兜底放行（否则「读不到设置」会变成「应用起不来」）。
     */
    val isLoaded: StateFlow<Boolean>

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setVibrationEnabled(enabled: Boolean)

    /** 主题配色编号（SRS FR-11.3：V1.3 起是 4 种主题色，不是深浅三态）。 */
    suspend fun setThemePaletteId(id: Int)

    suspend fun setSkinId(skinId: Int)
}

/**
 * 内存实现：M7 阶段的占位，应用重启后设置不保留（FR-14.2 尚未满足）。
 *
 * M8 引入 DataStore 后本类仅保留给测试使用。
 */
class InMemorySettingsRepository(
    initial: Settings = Settings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state.asStateFlow()

    /**
     * 内存实现没有「读盘」这一步，构造完成即已就绪，因此恒为 true。
     *
     * 用 `MutableStateFlow(true)` 而不是 `flowOf(true)`：类型是接口的一部分
     * （`StateFlow`），这里必须给出一个真正的 StateFlow。
     */
    override val isLoaded: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun setSoundEnabled(enabled: Boolean) =
        state.update { it.copy(soundEnabled = enabled) }

    override suspend fun setVibrationEnabled(enabled: Boolean) =
        state.update { it.copy(vibrationEnabled = enabled) }

    override suspend fun setThemePaletteId(id: Int) =
        state.update { it.copy(themePaletteId = id) }

    override suspend fun setSkinId(skinId: Int) =
        state.update { it.copy(skinId = skinId) }
}
