package com.ldxy.lianliankan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Progress
import com.ldxy.lianliankan.domain.model.Settings
import com.ldxy.lianliankan.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 基于 DataStore 的设置仓库（SRS FR-11.6 / FR-14.1 / FR-14.2）。
 *
 * 只负责把 [PreferencesMapping] 的纯映射接到 DataStore 的读写流程上，
 * 本身不含任何判断逻辑。
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<Settings> =
        dataStore.data.map(PreferencesMapping::readSettings)

    override suspend fun setSoundEnabled(enabled: Boolean) = update { it.copy(soundEnabled = enabled) }

    override suspend fun setVibrationEnabled(enabled: Boolean) =
        update { it.copy(vibrationEnabled = enabled) }

    override suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

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
