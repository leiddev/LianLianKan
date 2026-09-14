package com.ldxy.lianliankan.data

import com.ldxy.lianliankan.domain.config.LevelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 仓库层单测（开发计划 M7）。
 *
 * 两个内存实现是 M8 之前的占位，但它们承载了**真实的判断逻辑**：
 * 「是否刷新最佳分」（SRS FR-8.5 / FR-10.4）、「解锁下一关」（FR-2.4）、
 * 「清除进度」（FR-11.5）。这些逻辑在换成 DataStore 实现后不变，因此值得固定下来。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    @Before
    fun setUp() {
        // 仓库的写方法都是 suspend，但内部不做调度切换；给 Main 一个真实调度器即可
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================ 进度

    @Test
    fun `初始进度只解锁第 1 关`() = runTest {
        val repository = InMemoryProgressRepository()
        val progress = repository.progress.value

        assertTrue(progress.isUnlocked(1))
        assertFalse(progress.isUnlocked(2))
        assertEquals(0, progress.bestScoreOf(1))
        assertNull(progress.inProgressLevel)
    }

    @Test
    fun `通关后解锁下一关并返回是否刷新最佳分`() = runTest {
        val repository = InMemoryProgressRepository()

        assertTrue("首次通关应视为刷新记录", repository.recordCleared(level = 1, score = 320))
        assertTrue(repository.progress.value.isUnlocked(2))
        assertEquals(320, repository.progress.value.bestScoreOf(1))

        assertFalse("更低分不算刷新记录", repository.recordCleared(level = 1, score = 100))
        assertEquals("低分不应覆盖", 320, repository.progress.value.bestScoreOf(1))

        assertTrue("更高分应刷新记录", repository.recordCleared(level = 1, score = 500))
        assertEquals(500, repository.progress.value.bestScoreOf(1))
    }

    @Test
    fun `最高关卡不超过关卡总数`() = runTest {
        val repository = InMemoryProgressRepository()
        repository.recordCleared(level = LevelCatalog.levelCount, score = 999)

        assertEquals(LevelCatalog.levelCount, repository.progress.value.maxUnlockedLevel)
    }

    @Test
    fun `通关会清掉进行中标记`() = runTest {
        val repository = InMemoryProgressRepository()
        repository.setInProgressLevel(3)
        assertEquals(3, repository.progress.value.inProgressLevel)

        repository.recordCleared(level = 3, score = 100)
        assertNull("通关后不应再有进行中进度", repository.progress.value.inProgressLevel)
    }

    @Test
    fun `清除进度会回到初始状态`() = runTest {
        val repository = InMemoryProgressRepository()
        repository.recordCleared(level = 1, score = 500)
        repository.recordCleared(level = 2, score = 400)
        repository.setInProgressLevel(3)

        repository.clear()

        val progress = repository.progress.value
        assertEquals(1, progress.maxUnlockedLevel)
        assertTrue(progress.levelBestScores.isEmpty())
        assertEquals(0, progress.totalBestScore)
        assertNull(progress.inProgressLevel)
    }

    // ============================================================ 设置

    @Test
    fun `设置默认值与 SRS 7-2 数据字典一致`() = runTest {
        val settings = InMemorySettingsRepository().settings.value

        assertTrue("音效默认开启", settings.soundEnabled)
        assertTrue("振动默认开启", settings.vibrationEnabled)
        assertEquals("主题默认编号为 0（对应 ThemePalettes.DEFAULT_ID）", 0, settings.themePaletteId)
        assertEquals(0, settings.skinId)
    }

    @Test
    fun `修改设置立即反映到 StateFlow`() = runTest {
        val repository = InMemorySettingsRepository()

        repository.setSoundEnabled(false)
        repository.setVibrationEnabled(false)
        repository.setThemePaletteId(2)
        repository.setSkinId(2)

        val settings = repository.settings.value
        assertFalse(settings.soundEnabled)
        assertFalse(settings.vibrationEnabled)
        assertEquals(2, settings.themePaletteId)
        assertEquals(2, settings.skinId)
    }
}
