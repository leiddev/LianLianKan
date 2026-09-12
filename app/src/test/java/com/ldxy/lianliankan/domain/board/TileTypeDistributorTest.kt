package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.config.LevelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证开发计划 9.1 节「决策 D-1」的图案张数分配规则。
 *
 * 该表即 D-1 中经脚本核对过的 10 关分配结果（base 与 k），
 * 现在固化为断言，防止后续改动破坏「每类偶数、总和等于牌总数」这条 FR-3.1 前提。
 */
class TileTypeDistributorTest {

    private data class Expectation(val level: Int, val base: Int, val extraTypes: Int)

    /** D-1 验证表：关卡 → (base, k)。 */
    private val d1Table = listOf(
        Expectation(level = 1, base = 6, extraTypes = 0),
        Expectation(level = 2, base = 4, extraTypes = 6),
        Expectation(level = 3, base = 6, extraTypes = 5),
        Expectation(level = 4, base = 6, extraTypes = 2),
        Expectation(level = 5, base = 6, extraTypes = 2),
        Expectation(level = 6, base = 8, extraTypes = 0),
        Expectation(level = 7, base = 6, extraTypes = 7),
        Expectation(level = 8, base = 6, extraTypes = 4),
        Expectation(level = 9, base = 8, extraTypes = 0),
        Expectation(level = 10, base = 6, extraTypes = 9),
    )

    @Test
    fun `D-1 表覆盖全部 10 关`() {
        assertEquals(LevelCatalog.levelCount, d1Table.size)
        assertEquals(
            LevelCatalog.levels.map { it.level },
            d1Table.map { it.level },
        )
    }

    @Test
    fun `每关分配结果与 D-1 表一致`() {
        for (expectation in d1Table) {
            val config = LevelCatalog.configOf(expectation.level)
            val counts = TileTypeDistributor.countsFor(config.tileCount, config.typeCount)

            assertEquals(
                "第 ${expectation.level} 关：类型数不符",
                config.typeCount,
                counts.size,
            )
            assertEquals(
                "第 ${expectation.level} 关：牌总数不符",
                config.tileCount,
                counts.sum(),
            )

            val baseCount = counts.count { it == expectation.base }
            val elevatedCount = counts.count { it == expectation.base + 2 }
            assertEquals(
                "第 ${expectation.level} 关：base=${expectation.base} 的类型数量不符",
                config.typeCount - expectation.extraTypes,
                baseCount,
            )
            assertEquals(
                "第 ${expectation.level} 关：base+2 的类型数量不符",
                expectation.extraTypes,
                elevatedCount,
            )
        }
    }

    @Test
    fun `每类张数恒为正偶数且总和等于牌总数`() {
        for (config in LevelCatalog.levels) {
            val counts = TileTypeDistributor.countsFor(config.tileCount, config.typeCount)
            for ((type, count) in counts.withIndex()) {
                assertTrue(
                    "第 ${config.level} 关类型 $type 的张数 $count 不是正偶数",
                    count >= 2 && count % 2 == 0,
                )
            }
            assertEquals(config.tileCount, counts.sum())
        }
    }

    @Test
    fun `覆盖参数空间内的全部组合均满足不变量`() {
        // tileCount 为偶数且每类至少 2 张的前提下，规则必须始终自洽。
        for (typeCount in 1..30) {
            for (tileCount in (typeCount * 2)..240 step 2) {
                val counts = TileTypeDistributor.countsFor(tileCount, typeCount)
                assertEquals("typeCount=$typeCount tileCount=$tileCount", typeCount, counts.size)
                assertEquals("typeCount=$typeCount tileCount=$tileCount", tileCount, counts.sum())
                assertTrue(
                    "typeCount=$typeCount tileCount=$tileCount 存在非正偶数张数",
                    counts.all { it >= 2 && it % 2 == 0 },
                )
                assertTrue(
                    "typeCount=$typeCount tileCount=$tileCount 类间差异超过 2 张",
                    counts.max() - counts.min() <= 2,
                )
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `牌总数为奇数时拒绝`() {
        TileTypeDistributor.countsFor(tileCount = 45, typeCount = 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `牌总数不足以让每类分到 2 张时拒绝`() {
        TileTypeDistributor.countsFor(tileCount = 10, typeCount = 6)
    }
}
