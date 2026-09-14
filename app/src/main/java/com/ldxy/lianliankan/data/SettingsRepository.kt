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

    override suspend fun setSoundEnabled(enabled: Boolean) =
        state.update { it.copy(soundEnabled = enabled) }

    override suspend fun setVibrationEnabled(enabled: Boolean) =
        state.update { it.copy(vibrationEnabled = enabled) }

    override suspend fun setThemePaletteId(id: Int) =
        state.update { it.copy(themePaletteId = id) }

    override suspend fun setSkinId(skinId: Int) =
        state.update { it.copy(skinId = skinId) }
}
