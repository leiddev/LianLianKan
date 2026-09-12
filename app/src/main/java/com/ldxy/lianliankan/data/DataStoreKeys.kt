package com.ldxy.lianliankan.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

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
    val THEME_MODE = stringPreferencesKey("theme_mode")
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
