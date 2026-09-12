package com.ldxy.lianliankan.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.ldxy.lianliankan.domain.model.Progress
import com.ldxy.lianliankan.domain.model.Settings
import com.ldxy.lianliankan.domain.model.ThemeMode

/**
 * `Preferences` ↔ 领域模型的纯映射（SRS 7.2 数据字典）。
 *
 * 刻意与 DataStore 的读写流程分离：**这部分是真正会写错的代码**（键名、默认值、
 * 非法值兜底、动态键前缀），而 DataStore 本身只是管道。拆开之后它可以在 JVM 上直接单测，
 * 不必搭 Android 环境（SRS NFR-3.1）。
 *
 * 默认值一律取 SRS 7.2 数据字典中的「默认值」列。
 */
internal object PreferencesMapping {

    // ================================================================ 设置

    fun readSettings(preferences: Preferences): Settings = Settings(
        soundEnabled = preferences[DataStoreKeys.SOUND_ENABLED] ?: true,
        vibrationEnabled = preferences[DataStoreKeys.VIBRATION_ENABLED] ?: true,
        themeMode = preferences[DataStoreKeys.THEME_MODE].toThemeMode(),
        skinId = preferences[DataStoreKeys.SKIN_ID] ?: 0,
    )

    fun writeSettings(preferences: MutablePreferences, settings: Settings) {
        preferences[DataStoreKeys.SOUND_ENABLED] = settings.soundEnabled
        preferences[DataStoreKeys.VIBRATION_ENABLED] = settings.vibrationEnabled
        preferences[DataStoreKeys.THEME_MODE] = settings.themeMode.name
        preferences[DataStoreKeys.SKIN_ID] = settings.skinId
    }

    /**
     * 主题模式字符串 → 枚举。
     *
     * 存储值可能来自旧版本或被外部改写，因此非法值一律回退到默认的「跟随系统」（SYSTEM），
     * 而不是抛异常 —— 设置损坏不应该让应用起不来。
     */
    private fun String?.toThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

    // ================================================================ 进度

    fun readProgress(preferences: Preferences): Progress {
        val bestScores = buildMap {
            for ((key, value) in preferences.asMap()) {
                val level = key.name
                    .takeIf { it.startsWith(DataStoreKeys.LEVEL_BEST_SCORE_PREFIX) }
                    ?.removePrefix(DataStoreKeys.LEVEL_BEST_SCORE_PREFIX)
                    ?.toIntOrNull()
                    ?: continue
                val score = value as? Int ?: continue
                put(level, score)
            }
        }

        val inProgress = preferences[DataStoreKeys.IN_PROGRESS_LEVEL]
            ?.takeIf { it > 0 }

        return Progress(
            maxUnlockedLevel = preferences[DataStoreKeys.MAX_UNLOCKED_LEVEL] ?: 1,
            levelBestScores = bestScores,
            totalBestScore = preferences[DataStoreKeys.TOTAL_BEST_SCORE] ?: 0,
            inProgressLevel = inProgress,
        )
    }

    fun writeProgress(preferences: MutablePreferences, progress: Progress) {
        preferences[DataStoreKeys.MAX_UNLOCKED_LEVEL] = progress.maxUnlockedLevel
        preferences[DataStoreKeys.TOTAL_BEST_SCORE] = progress.totalBestScore
        // SRS 7.2 用 0 表示「无进行中关卡」
        preferences[DataStoreKeys.IN_PROGRESS_LEVEL] = progress.inProgressLevel ?: 0
        for ((level, score) in progress.levelBestScores) {
            preferences[DataStoreKeys.levelBestScore(level)] = score
        }
        preferences[DataStoreKeys.SCHEMA_VERSION_KEY] = DataStoreKeys.SCHEMA_VERSION
    }

    /** 清空全部业务键（SRS FR-11.5 的「清除进度」）。 */
    fun clearProgress(preferences: MutablePreferences) {
        preferences.remove(DataStoreKeys.MAX_UNLOCKED_LEVEL)
        preferences.remove(DataStoreKeys.TOTAL_BEST_SCORE)
        preferences.remove(DataStoreKeys.IN_PROGRESS_LEVEL)
        for (key in preferences.asMap().keys.toList()) {
            if (key.name.startsWith(DataStoreKeys.LEVEL_BEST_SCORE_PREFIX)) {
                preferences.remove(key)
            }
        }
    }
}
