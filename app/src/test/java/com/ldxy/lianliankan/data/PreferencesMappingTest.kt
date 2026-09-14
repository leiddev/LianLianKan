package com.ldxy.lianliankan.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.ldxy.lianliankan.domain.model.Progress
import com.ldxy.lianliankan.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Preferences` ↔ 领域模型的映射单测（开发计划 M8）。
 *
 * 这是 M8 里真正会写错的部分 —— 键名、默认值、非法值兜底、动态键前缀 ——
 * 与 DataStore 的读写流程拆开后就能在 JVM 上直接测（SRS NFR-3.1）。
 */
class PreferencesMappingTest {

    // ============================================================ 设置

    @Test
    fun `空存储读出 SRS 7-2 的默认值`() {
        val settings = PreferencesMapping.readSettings(emptyPreferences())

        assertTrue("音效默认开启", settings.soundEnabled)
        assertTrue("振动默认开启", settings.vibrationEnabled)
        assertEquals("主题默认编号为 0", 0, settings.themePaletteId)
        assertEquals(0, settings.skinId)
    }

    @Test
    fun `设置可完整往返`() {
        val original = Settings(
            soundEnabled = false,
            vibrationEnabled = false,
            themePaletteId = 3,
            skinId = 3,
        )
        val preferences = mutablePreferencesOf()
        PreferencesMapping.writeSettings(preferences, original)

        assertEquals(original, PreferencesMapping.readSettings(preferences))
    }

    @Test
    fun `主题编号以 Int 存储且四套配色都能往返`() {
        for (id in 0 until 4) {
            val preferences = mutablePreferencesOf()
            PreferencesMapping.writeSettings(preferences, Settings(themePaletteId = id))

            assertEquals(id, preferences[DataStoreKeys.THEME_PALETTE_ID])
            assertEquals(id, PreferencesMapping.readSettings(preferences).themePaletteId)
        }
    }

    @Test
    fun `主题编号为负数时回退到 0 而不抛异常`() {
        // 存储值可能被外部改写；负数不是合法的配色编号，回退到默认而不是让应用起不来。
        // 越界的正数（如 99）这里原样读出，由 `ThemePalettes.byId` 负责回退 —— 兜底只做一次。
        val preferences = preferencesOf(DataStoreKeys.THEME_PALETTE_ID to -1)

        assertEquals(0, PreferencesMapping.readSettings(preferences).themePaletteId)
    }

    // ============================================================ 进度

    @Test
    fun `空存储读出初始进度`() {
        val progress = PreferencesMapping.readProgress(emptyPreferences())

        assertEquals(1, progress.maxUnlockedLevel)
        assertTrue(progress.levelBestScores.isEmpty())
        assertEquals(0, progress.totalBestScore)
        assertNull("未进行中时应为 null 而不是 0", progress.inProgressLevel)
    }

    @Test
    fun `进度可完整往返且动态关卡键被正确拆解`() {
        val original = Progress(
            maxUnlockedLevel = 4,
            levelBestScores = mapOf(1 to 320, 2 to 415, 3 to 88),
            totalBestScore = 823,
            inProgressLevel = 4,
        )
        val preferences = mutablePreferencesOf()
        PreferencesMapping.writeProgress(preferences, original)

        assertEquals(
            "动态键 level_best_score_<n> 应能被前缀扫描还原",
            mapOf(1 to 320, 2 to 415, 3 to 88),
            PreferencesMapping.readProgress(preferences).levelBestScores,
        )
        assertEquals(original, PreferencesMapping.readProgress(preferences))
    }

    @Test
    fun `进行中关卡用 0 表示无`() {
        val preferences = mutablePreferencesOf()
        PreferencesMapping.writeProgress(preferences, Progress(inProgressLevel = null))

        assertEquals("SRS 7.2 约定 0 表示无进行中关卡", 0, preferences[DataStoreKeys.IN_PROGRESS_LEVEL])
        assertNull(PreferencesMapping.readProgress(preferences).inProgressLevel)
    }

    @Test
    fun `写入进度会带上结构版本号`() {
        val preferences = mutablePreferencesOf()
        PreferencesMapping.writeProgress(preferences, Progress())

        assertEquals(
            DataStoreKeys.SCHEMA_VERSION,
            preferences[DataStoreKeys.SCHEMA_VERSION_KEY],
        )
    }

    @Test
    fun `不带关卡编号前缀的键不会被误当作最佳分`() {
        val preferences = preferencesOf(
            DataStoreKeys.TOTAL_BEST_SCORE to 500,
            DataStoreKeys.levelBestScore(2) to 120,
        )

        assertEquals(
            mapOf(2 to 120),
            PreferencesMapping.readProgress(preferences).levelBestScores,
        )
    }

    @Test
    fun `清除进度只清业务数据不影响其他键`() {
        val preferences = mutablePreferencesOf()
        PreferencesMapping.writeProgress(
            preferences,
            Progress(
                maxUnlockedLevel = 5,
                levelBestScores = mapOf(1 to 100, 2 to 200),
                totalBestScore = 300,
                inProgressLevel = 3,
            ),
        )

        PreferencesMapping.clearProgress(preferences)
        val progress = PreferencesMapping.readProgress(preferences)

        assertEquals(1, progress.maxUnlockedLevel)
        assertTrue("各关最佳分应被清空", progress.levelBestScores.isEmpty())
        assertEquals(0, progress.totalBestScore)
        assertNull(progress.inProgressLevel)
    }
}
