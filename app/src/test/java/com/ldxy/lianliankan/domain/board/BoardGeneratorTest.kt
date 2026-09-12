package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.config.LevelCatalog
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 棋盘生成器单测，覆盖验收标准 AC-01：
 * 「每次生成的牌总数为偶数、每类型数量为偶数、布局随机」。
 */
class BoardGeneratorTest {

    private companion object {
        /** 固定种子让测试可复现（SRS NFR-2.1）。 */
        const val SEED = 20260912L
        const val ITERATIONS = 100
    }

    private fun generator(seed: Long = SEED) = BoardGenerator(Random(seed))

    @Test
    fun `AC-01 每关生成 100 次 - 牌总数为偶数且每类张数为偶数`() {
        for (config in LevelCatalog.levels) {
            val generator = generator()
            repeat(ITERATIONS) { round ->
                val board = generator.generate(config)
                val where = "第 ${config.level} 关第 $round 次生成"

                assertEquals("$where：牌总数", config.tileCount, board.tileCount)
                assertEquals("$where：牌总数为奇数", 0, board.tileCount % 2)
                assertEquals("$where：剩余牌数", config.tileCount, board.remainingCount)

                val counts = board.remainingCountsByType()
                assertEquals("$where：图案种类数", config.typeCount, counts.size)
                for ((type, count) in counts) {
                    assertEquals("$where：类型 $type 张数为奇数", 0, count % 2)
                }
                assertEquals("$where：各类型张数之和", config.tileCount, counts.values.sum())
            }
        }
    }

    @Test
    fun `AC-01 每关生成的类型分布符合 D-1 分配规则`() {
        for (config in LevelCatalog.levels) {
            val expected = TileTypeDistributor.countsFor(config.tileCount, config.typeCount)
                .withIndex()
                .associate { (type, count) -> type to count }

            val board = generator().generate(config)
            assertEquals(
                "第 ${config.level} 关类型分布与分配规则不符",
                expected,
                board.remainingCountsByType(),
            )
        }
    }

    @Test
    fun `棋盘尺寸与配置一致且无空位`() {
        for (config in LevelCatalog.levels) {
            val board = generator().generate(config)
            assertEquals("第 ${config.level} 关行数", config.rows, board.rows)
            assertEquals("第 ${config.level} 关列数", config.cols, board.cols)

            for (row in 0 until config.rows) {
                for (col in 0 until config.cols) {
                    val tile = board.tileAt(row, col)
                    assertNotNull("第 ${config.level} 关 ($row, $col) 为空", tile)
                    assertEquals("牌坐标与存放位置不一致", row, tile!!.row)
                    assertEquals("牌坐标与存放位置不一致", col, tile.col)
                }
            }
        }
    }

    @Test
    fun `牌 id 在棋盘内唯一且覆盖 0 到 总数减一`() {
        for (config in LevelCatalog.levels) {
            val board = generator().generate(config)
            val ids = board.remainingTiles().map { it.id }.sorted()
            assertEquals(
                "第 ${config.level} 关 id 不连续或重复",
                (0 until config.tileCount).toList(),
                ids,
            )
        }
    }

    @Test
    fun `所有牌初始为未选中状态`() {
        val board = generator().generate(LevelCatalog.firstLevel)
        assertTrue(
            "初始棋盘不应存在选中态或提示态",
            board.remainingTiles().all { it.state.name == "NORMAL" },
        )
    }

    @Test
    fun `AC-01 同一关卡多次生成的布局不同`() {
        for (config in LevelCatalog.levels) {
            val generator = generator()
            val layouts = HashSet<List<Int>>()
            repeat(20) {
                layouts += generator.generate(config).remainingTiles().map { tile -> tile.type }
            }
            assertTrue(
                "第 ${config.level} 关 20 次生成只得到 ${layouts.size} 种布局，随机性不足",
                layouts.size > 1,
            )
        }
    }

    @Test
    fun `相同种子生成结果可复现`() {
        val config = LevelCatalog.configOf(4)
        val first = BoardGenerator(Random(42)).generate(config)
        val second = BoardGenerator(Random(42)).generate(config)
        assertEquals("同种子应产生完全相同的棋盘", first, second)

        val third = BoardGenerator(Random(43)).generate(config)
        assertTrue("不同种子不应产生相同棋盘", first != third)
    }
}
