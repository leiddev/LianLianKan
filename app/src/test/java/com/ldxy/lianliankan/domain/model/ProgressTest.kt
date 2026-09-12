package com.ldxy.lianliankan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Progress] 单测：解锁、最佳分刷新与「继续游戏」标记（SRS FR-2.4 / FR-8.5 / FR-14.3）。 */
class ProgressTest {

    @Test
    fun `默认仅解锁第 1 关且无进行中进度`() {
        val progress = Progress()
        assertEquals(1, progress.maxUnlockedLevel)
        assertTrue(progress.isUnlocked(1))
        assertFalse(progress.isUnlocked(2))
        assertEquals(0, progress.bestScoreOf(1))
        assertNull(progress.inProgressLevel)
    }

    @Test
    fun `通关后解锁下一关并记录最佳分`() {
        val progress = Progress().recordCleared(level = 1, score = 320, levelCount = 10)

        assertEquals(2, progress.maxUnlockedLevel)
        assertTrue(progress.isUnlocked(2))
        assertFalse(progress.isUnlocked(3))
        assertEquals(320, progress.bestScoreOf(1))
        assertEquals(320, progress.totalBestScore)
        assertNull("通关后不应残留进行中进度", progress.inProgressLevel)
    }

    @Test
    fun `最佳分只在刷新时更新`() {
        val first = Progress().recordCleared(level = 1, score = 500, levelCount = 10)
        val lower = first.recordCleared(level = 1, score = 300, levelCount = 10)
        val higher = first.recordCleared(level = 1, score = 700, levelCount = 10)

        assertEquals("更低分不应覆盖最佳分", 500, lower.bestScoreOf(1))
        assertEquals(500, lower.totalBestScore)
        assertEquals(700, higher.bestScoreOf(1))
        assertEquals(700, higher.totalBestScore)
    }

    @Test
    fun `最高关卡不超过关卡总数`() {
        val progress = Progress().recordCleared(level = 10, score = 1000, levelCount = 10)
        assertEquals(10, progress.maxUnlockedLevel)
        assertTrue(progress.isUnlocked(10))
    }

    @Test
    fun `重复通关同一关不会回退已解锁关卡`() {
        val progress = Progress()
            .recordCleared(level = 1, score = 100, levelCount = 10)
            .recordCleared(level = 2, score = 200, levelCount = 10)
            .recordCleared(level = 1, score = 150, levelCount = 10)

        assertEquals(3, progress.maxUnlockedLevel)
        assertEquals(150, progress.bestScoreOf(1))
        assertEquals(200, progress.bestScoreOf(2))
    }
}
