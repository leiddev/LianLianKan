package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.boardOf
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Board
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 洗牌单测，覆盖 SRS FR-3.4 / FR-6.2 与验收标准 AC-08：
 * 「无解时自动重排并提示，且重排后可解」。
 */
class ShuffleServiceTest {

    // ============================================================ 不变量

    @Test
    fun `洗牌保持牌数、id 集合与类型分布不变`() {
        val generator = BoardGenerator(Random(3L))
        val service = ShuffleService(Random(4L))

        for (config in LevelCatalog.levels) {
            val board = generator.generate(config)
            val shuffled = service.shuffle(board)

            assertEquals("第 ${config.level} 关牌数", board.remainingCount, shuffled.remainingCount)
            assertEquals(
                "第 ${config.level} 关 id 集合",
                board.remainingTiles().map { it.id }.toSet(),
                shuffled.remainingTiles().map { it.id }.toSet(),
            )
            assertEquals(
                "第 ${config.level} 关类型分布",
                board.remainingCountsByType(),
                shuffled.remainingCountsByType(),
            )
            assertEquals("棋盘尺寸不应改变", board.rows, shuffled.rows)
            assertEquals("棋盘尺寸不应改变", board.cols, shuffled.cols)
        }
    }

    @Test
    fun `洗牌只重排未消除的牌 - 已消除的格子保持为空`() {
        val board = boardOf(
            "AABB",
            "CCDD",
        )
        val afterElimination = board.without(0, 0).without(0, 1) // 消掉两张 A

        val shuffled = ShuffleService(Random(5L)).shuffle(afterElimination)

        assertNull("已消除的格子应仍为空", shuffled.tileAt(0, 0))
        assertNull("已消除的格子应仍为空", shuffled.tileAt(0, 1))
        assertEquals(6, shuffled.remainingCount)
        assertEquals(
            "洗牌不应改变类型分布",
            afterElimination.remainingCountsByType(),
            shuffled.remainingCountsByType(),
        )
        assertTrue("洗牌后必须有解", DeadlockDetector.hasMove(shuffled))
    }

    @Test
    fun `洗牌后每张牌的坐标与其所在格子一致`() {
        val board = BoardGenerator(Random(6L)).generate(LevelCatalog.configOf(6))
        val shuffled = ShuffleService(Random(7L)).shuffle(board)

        for (tile in shuffled.remainingTiles()) {
            assertEquals("牌的 row 与落位不一致", tile.row, shuffled.tileAt(tile.row, tile.col)!!.row)
            assertEquals("牌的 col 与落位不一致", tile.col, shuffled.tileAt(tile.row, tile.col)!!.col)
            assertEquals("落位上的不是同一张牌", tile.id, shuffled.tileAt(tile.row, tile.col)!!.id)
        }
    }

    @Test
    fun `剩余牌不足两张时原样返回`() {
        val service = ShuffleService(Random(8L))

        val empty = Board.empty(rows = 2, cols = 2)
        assertSame(empty, service.shuffle(empty))

        val single = boardOf("A.", "..")
        assertSame(single, service.shuffle(single))
    }

    @Test
    fun `相同种子洗牌结果可复现`() {
        val board = BoardGenerator(Random(9L)).generate(LevelCatalog.configOf(4))

        val first = ShuffleService(Random(42L)).shuffle(board)
        val second = ShuffleService(Random(42L)).shuffle(board)
        assertEquals("同种子应产生相同结果", first, second)

        val third = ShuffleService(Random(43L)).shuffle(board)
        assertTrue("不同种子不应产生相同结果", first != third)
    }

    // ============================================================ 保证有解（AC-08）

    @Test
    fun `AC-08 随机棋盘洗牌后保证有解`() {
        val generator = BoardGenerator(Random(20260912L))
        val service = ShuffleService(Random(20260913L))

        for (config in LevelCatalog.levels) {
            repeat(10) {
                val board = generator.generate(config)
                val shuffled = service.shuffle(board)
                assertTrue(
                    "第 ${config.level} 关洗牌后应存在可行步",
                    DeadlockDetector.hasMove(shuffled),
                )
            }
        }
    }

    @Test
    fun `AC-08 死局棋盘连续洗牌 200 次恒可解`() {
        // 已知死局：2x2 满盘、两组同类型牌各自对角
        val deadlocked = boardOf(
            "AB",
            "BA",
        )
        assertFalse("前置条件：该棋盘应为死局", DeadlockDetector.hasMove(deadlocked))

        val service = ShuffleService(Random(20260912L))
        var board = deadlocked
        repeat(200) { round ->
            board = service.shuffle(board)
            assertTrue(
                "第 ${round + 1} 次洗牌后应存在可行步",
                DeadlockDetector.hasMove(board),
            )
            assertEquals("洗牌不应改变牌数", 4, board.remainingCount)
        }
    }

    // ============================================================ 兜底策略

    @Test
    fun `成对重排兜底在满盘到稀疏的各种局面下都保证有解`() {
        // maxAttempts = 0 表示跳过全部随机尝试，强制走 placeGuaranteedSolvable 兜底路径，
        // 从而直接检验类注释里那条构造性证明。
        val generator = BoardGenerator(Random(20260914L))
        val forcing = ShuffleService(Random(20260915L), maxAttempts = 0)

        var checkedStates = 0
        for (config in LevelCatalog.levels) {
            var board = generator.generate(config)

            while (board.remainingCount >= 2) {
                val shuffled = forcing.shuffle(board)

                assertEquals(
                    "第 ${config.level} 关剩余 ${board.remainingCount} 张时牌数被改变",
                    board.remainingCount,
                    shuffled.remainingCount,
                )
                assertEquals(
                    "第 ${config.level} 关剩余 ${board.remainingCount} 张时类型分布被改变",
                    board.remainingCountsByType(),
                    shuffled.remainingCountsByType(),
                )
                assertTrue(
                    "第 ${config.level} 关剩余 ${board.remainingCount} 张时兜底结果无解",
                    DeadlockDetector.hasMove(shuffled),
                )
                checkedStates++

                // 消掉一对同类型牌，制造更稀疏的局面继续施压
                val tiles = shuffled.remainingTiles()
                val first = tiles.first()
                val partner = tiles.firstOrNull { it.id != first.id && it.type == first.type }
                    ?: break
                board = shuffled.without(first.row, first.col).without(partner.row, partner.col)
            }
        }

        assertTrue("应实际检验足够多的剩余牌局面", checkedStates > 200)
    }

    @Test
    fun `兜底策略在只剩两张牌时也能给出可连通的摆放`() {
        val forcing = ShuffleService(Random(1L), maxAttempts = 0)
        val board = boardOf(
            "A.",
            ".A",
        )
        val shuffled = forcing.shuffle(board)

        assertEquals(2, shuffled.remainingCount)
        assertTrue("只剩两张同类型牌时必须可连通", DeadlockDetector.hasMove(shuffled))
    }

    @Test
    fun `重试次数为负数时构造失败`() {
        try {
            ShuffleService(Random(1L), maxAttempts = -1)
            throw AssertionError("负数重试次数应当被拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("-1"))
        }
    }

    // ============================================================ 与自动洗牌配合

    @Test
    fun `对已死局的真实棋盘洗牌后可继续游戏`() {
        // 直接用生成的棋盘，反复「洗牌 → 消一对」，全程保持有解
        val generator = BoardGenerator(Random(20260916L))
        val service = ShuffleService(Random(20260917L))
        var board = generator.generate(LevelCatalog.configOf(2))

        var steps = 0
        while (board.remainingCount >= 2) {
            board = service.shuffle(board)

            val move = DeadlockDetector.findMove(board)
            assertTrue("洗牌后必须能继续消除", move != null)
            board = board.without(move!!.first.row, move.first.col)
                .without(move.second.row, move.second.col)
            steps++

            assertEquals(
                "消除后类型计数应保持偶数",
                0,
                board.remainingCountsByType().values.count { it % 2 != 0 },
            )
        }

        assertTrue("应实际推进了足够多的步数", steps > 20)
        assertTrue("最终应清盘", board.isCleared)
    }
}
