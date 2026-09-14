package com.ldxy.lianliankan.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * DataStore 键定义，逐条对应 SRS 7.2「数据字典」。
 *
 * 集中在一处，便于核对需求与实现是否一致；也避免字符串字面量散落各处写错。
 */
internal object DataStoreKeys {

    /** DataStore 文件名与结构版本（SRS FR-14.5）。 */
    const val STORE_NAME: String = "lianliankan"
    const val SCHEMA_VERSION: Int = 1

    // ---- 设置 ----
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")

    /**
     * 主题配色编号（SRS FR-11.3）。
     *
     * V1.3 起以本键取代旧的字符串键 `theme_mode`：**旧键直接弃用、不做数据迁移** ——
     * 读不到就是默认的第 0 套配色，用户重选一次主题无成本，而为一次性升级写迁移代码
     * 是长期负担（旧值 `"SYSTEM"` 之类的语义在新方案下也没有对应物）。
     */
    val THEME_PALETTE_ID = intPreferencesKey("theme_palette_id")
    val SKIN_ID = intPreferencesKey("skin_id")

    // ---- 进度 ----
    val MAX_UNLOCKED_LEVEL = intPreferencesKey("max_unlocked_level")
    val TOTAL_BEST_SCORE = intPreferencesKey("total_best_score")
    val IN_PROGRESS_LEVEL = intPreferencesKey("in_progress_level")
    val SCHEMA_VERSION_KEY = intPreferencesKey("schema_version")

    /**
     * 各关最佳分的键前缀（SRS 7.2 的 `level_best_score_<n>`）。
     *
     * 关卡编号无法预先枚举（新增关卡只需改配置表），因此这类键按前缀动态拼装与扫描。
     */
    const val LEVEL_BEST_SCORE_PREFIX: String = "level_best_score_"

    fun levelBestScore(level: Int) = intPreferencesKey("$LEVEL_BEST_SCORE_PREFIX$level")
}
