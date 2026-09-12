package com.ldxy.lianliankan.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 核对 [LevelCatalog] 与 SRS 附录 9.3「关卡难度配置表」逐行一致。 */
class LevelCatalogTest {

    /** SRS 9.3 表格原文：(关卡, 列, 行, 牌总数, 图案种类, 限时, 提示, 洗牌)。 */
    private val srsTable = listOf(
        intArrayOf(1, 6, 8, 48, 8, 180, 3, 3),
        intArrayOf(2, 6, 8, 48, 9, 180, 3, 3),
        intArrayOf(3, 8, 8, 64, 9, 180, 3, 3),
        intArrayOf(4, 6, 10, 60, 10, 180, 3, 3),
        intArrayOf(5, 8, 8, 64, 10, 175, 3, 3),
        intArrayOf(6, 8, 10, 80, 10, 170, 3, 3),
        intArrayOf(7, 8, 10, 80, 11, 165, 3, 3),
        intArrayOf(8, 8, 10, 80, 12, 160, 3, 3),
        intArrayOf(9, 8, 12, 96, 12, 155, 2, 2),
        intArrayOf(10, 8, 12, 96, 13, 150, 2, 2),
    )

    @Test
    fun `关卡总数为 10`() {
        assertEquals(10, LevelCatalog.levelCount)
        assertEquals(10, srsTable.size)
    }

    @Test
    fun `每关配置与 SRS 9-3 表一致`() {
        for (row in srsTable) {
            val config = LevelCatalog.configOf(row[0])
            assertEquals("第 ${row[0]} 关列数", row[1], config.cols)
            assertEquals("第 ${row[0]} 关行数", row[2], config.rows)
            assertEquals("第 ${row[0]} 关牌总数", row[3], config.tileCount)
            assertEquals("第 ${row[0]} 关图案种类", row[4], config.typeCount)
            assertEquals("第 ${row[0]} 关限时", row[5], config.timeLimitSeconds)
            assertEquals("第 ${row[0]} 关提示次数", row[6], config.hintCount)
            assertEquals("第 ${row[0]} 关洗牌次数", row[7], config.shuffleCount)
        }
    }

    @Test
    fun `关卡编号连续且从 1 开始`() {
        assertEquals((1..10).toList(), LevelCatalog.levels.map { it.level })
    }

    @Test
    fun `难度趋势 - 图案种类不下降且限时不增加`() {
        val levels = LevelCatalog.levels
        for (i in 1 until levels.size) {
            val previous = levels[i - 1]
            val current = levels[i]
            assertTrue(
                "第 ${current.level} 关图案种类少于上一关",
                current.typeCount >= previous.typeCount,
            )
            assertTrue(
                "第 ${current.level} 关限时高于上一关",
                current.timeLimitSeconds <= previous.timeLimitSeconds,
            )
        }

        // SRS 9.4 第 2 条：难度递增方式 = 棋盘变大 + 图案种类变多 + 限时略减
        assertTrue(levels.last().tileCount > levels.first().tileCount)
        assertTrue(levels.last().typeCount > levels.first().typeCount)
        assertTrue(levels.last().timeLimitSeconds < levels.first().timeLimitSeconds)
    }

    @Test
    fun `记录 SRS 9-3 表中第 4 关牌总数低于第 3 关这一非单调点`() {
        // 现象来自 SRS 附录 9.3 原表：第 3 关 8x8=64，第 4 关 6x10=60。
        // 即牌总数序列为 48,48,64,60,64,80,80,80,96,96 —— 在 3→4 处回落一次。
        // 这不是实现错误，此处显式断言以免日后被误判为回归（详见报告中的 SRS 观察项）。
        // 难度仍整体上升：第 4 关图案种类与第 3 关相同但少 4 张牌，
        // 真正拉开难度的是后续关卡的棋盘尺寸与限时。
        assertEquals(64, LevelCatalog.configOf(3).tileCount)
        assertEquals(60, LevelCatalog.configOf(4).tileCount)

        var dips = 0
        val levels = LevelCatalog.levels
        for (i in 1 until levels.size) {
            if (levels[i].tileCount < levels[i - 1].tileCount) dips++
        }
        assertEquals("牌总数序列只应有 1 处回落", 1, dips)
    }

    @Test
    fun `配置表满足 FR-3-1 的偶数前提`() {
        for (config in LevelCatalog.levels) {
            assertEquals(
                "第 ${config.level} 关牌总数为奇数",
                0,
                config.tileCount % 2,
            )
            assertTrue(
                "第 ${config.level} 关每类图案不足 2 张",
                config.tileCount >= config.typeCount * 2,
            )
        }
    }

    @Test
    fun `configOf 对非法关卡编号抛出异常`() {
        assertFalse(LevelCatalog.hasLevel(0))
        assertFalse(LevelCatalog.hasLevel(11))
        assertTrue(LevelCatalog.hasLevel(1))
        assertTrue(LevelCatalog.hasLevel(10))

        try {
            LevelCatalog.configOf(0)
            throw AssertionError("非法关卡编号应当抛异常")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("0"))
        }
    }
}
