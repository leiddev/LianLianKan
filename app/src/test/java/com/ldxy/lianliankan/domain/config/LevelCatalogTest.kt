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
        intArrayOf(4, 8, 8, 64, 10, 180, 3, 3),
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
    fun `牌总数随关卡单调不减`() {
        // SRS 9.3 自 V1.2 起修正了第 4 关规格（6×10 → 8×8），使牌总数序列
        // 48, 48, 64, 64, 64, 80, 80, 80, 96, 96 单调不减。
        val levels = LevelCatalog.levels
        for (i in 1 until levels.size) {
            val previous = levels[i - 1]
            val current = levels[i]
            assertTrue(
                "第 ${current.level} 关牌总数 ${current.tileCount} 少于第 ${previous.level} 关的 ${previous.tileCount}",
                current.tileCount >= previous.tileCount,
            )
        }
    }

    @Test
    fun `棋盘规格按 2 至 3 关一组递增`() {
        // 这条分组规律本身就是 SRS 9.3 的设计意图，也是 V1.2 修正第 4 关的依据：
        // 原表里唯独第 4 关（6×10）脱离了分组规律。
        val groups = LevelCatalog.levels
            .groupBy { it.cols to it.rows }
            .map { (size, configs) -> size to configs.map { it.level } }

        assertEquals(
            listOf(
                (6 to 8) to listOf(1, 2),
                (8 to 8) to listOf(3, 4, 5),
                (8 to 10) to listOf(6, 7, 8),
                (8 to 12) to listOf(9, 10),
            ),
            groups,
        )
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
