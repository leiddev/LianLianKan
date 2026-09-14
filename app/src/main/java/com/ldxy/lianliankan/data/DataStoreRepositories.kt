package com.ldxy.lianliankan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Progress
import com.ldxy.lianliankan.domain.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 基于 DataStore 的设置仓库（SRS FR-11.6 / FR-14.1 / FR-14.2）。
 *
 * 只负责把 [PreferencesMapping] 的纯映射接到 DataStore 的读写流程上，
 * 本身不含任何判断逻辑。
 *
 * V1.4 例外：多了一个 [isLoaded] 的**预热**职责，见构造参数 `preloadScope` 的说明。
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    /**
     * 预热 [isLoaded] 用的作用域。
     *
     * ### 为什么仓库要自持一个作用域
     * [isLoaded] 的消费者（启动画面的放行条件）在 `setContent` **之前**
     * 就被系统每帧询问，也就是说它必须自己先动起来，不可能等界面来订阅
     * [settings] 才被动开始读盘。仓库本身没有生命周期，因此这里单开一个
     * 只做一件事的作用域。它随 Activity 一起被丢弃，代价可忽略。
     *
     * 暴露成构造参数是为了让测试能注入 `TestScope`，
     * 从而不必依赖真实 IO 线程的时序。
     */
    preloadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SettingsRepository {

    private val loaded = MutableStateFlow(false)

    override val isLoaded: StateFlow<Boolean> = loaded.asStateFlow()

    override val settings: Flow<Settings> =
        dataStore.data.map(PreferencesMapping::readSettings)

    init {
        preloadScope.launch {
            // 只取首个值：dataStore.data 的首个发射即代表「磁盘已经读过一次」。
            // 这里刻意不 try/catch —— 读盘失败时 loaded 保持 false，
            // 放行与否由 MainActivity 的超时兜底**一处**决定，
            // 避免「仓库说可以、外面说不行」这种两处各自判断的分裂。
            dataStore.data.first()
            loaded.value = true
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) = update { it.copy(soundEnabled = enabled) }

    override suspend fun setVibrationEnabled(enabled: Boolean) =
        update { it.copy(vibrationEnabled = enabled) }

    override suspend fun setThemePaletteId(id: Int) = update { it.copy(themePaletteId = id) }

    override suspend fun setSkinId(skinId: Int) = update { it.copy(skinId = skinId) }

    private suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { preferences ->
            PreferencesMapping.writeSettings(preferences, transform(PreferencesMapping.readSettings(preferences)))
        }
    }
}

/**
 * 基于 DataStore 的进度仓库（SRS FR-2 / FR-10.4 / FR-14）。
 *
 * 「是否刷新最佳分」的比较沿用领域模型 `Progress.recordCleared`，
 * 这里不重复实现该规则。
 */
class DataStoreProgressRepository(
    private val dataStore: DataStore<Preferences>,
) : ProgressRepository {

    override val progress: Flow<Progress> =
        dataStore.data.map(PreferencesMapping::readProgress)

    override suspend fun recordCleared(level: Int, score: Int): Boolean {
        var isNewRecord = false
        dataStore.edit { preferences ->
            val current = PreferencesMapping.readProgress(preferences)
            isNewRecord = score > current.bestScoreOf(level)
            val updated = current.recordCleared(level, score, LevelCatalog.levelCount)
            PreferencesMapping.writeProgress(preferences, updated)
        }
        return isNewRecord
    }

    override suspend fun setInProgressLevel(level: Int?) {
        dataStore.edit { preferences ->
            val current = PreferencesMapping.readProgress(preferences)
            // 只在「进行中的关卡确实变了」时落盘，避免每次进入关卡都写一次磁盘
            if (current.inProgressLevel != level) {
                PreferencesMapping.writeProgress(preferences, current.copy(inProgressLevel = level))
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit(PreferencesMapping::clearProgress)
    }
}
