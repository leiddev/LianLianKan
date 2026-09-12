package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.at
import com.ldxy.lianliankan.domain.boardOf
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.path.PathFinder
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 死局检测单测，覆盖 SRS FR-6.1 与 AC-08 的检测部分。 */
class DeadlockDetectorTest {

    // ============================================================ 有解局面

    @Test
    fun `相邻的同类型牌构成可行步`() {
        val board = boardOf("AABB")
        val move = DeadlockDetector.findMove(board)

        assertNotNull(move)
        assertEquals(
            "返回的两张牌必须同类型",
            move!!.first.type,
            move.second.type,
        )
        assertEquals("该局面应走 0 拐点直线", listOf(at(0, 0), at(0, 1)), move.path.points)
    }

    @Test
    fun `同一行隔着空格的同类型牌构成可行步`() {
        val board = boardOf("A..A")
        val move = DeadlockDetector.findMove(board)

        assertNotNull(move)
        assertEquals("中间全为空，应走 0 拐点直线", 0, move!!.path.turns)
    }

    @Test
    fun `同类型牌被阻挡但可绕外侧时仍算可行步`() {
        // 中间被 B 挡住，直线作废；但可绕棋盘外侧连通，因此不是死局
        val board = boardOf("A.B.A")
        val move = DeadlockDetector.findMove(board)

        assertNotNull("能绕行就说明还有解", move)
        assertEquals("被阻挡后需绕行，拐点数为 2", 2, move!!.path.turns)
    }

    @Test
    fun `死局棋盘经洗牌后可检测到可行步`() {
        val deadlocked = boardOf(
            "AB",
            "BA",
        )
        assertFalse(DeadlockDetector.hasMove(deadlocked))

        val shuffled = ShuffleService(Random(20260912L)).shuffle(deadlocked)
        assertTrue("洗牌后应检测到可行步", DeadlockDetector.hasMove(shuffled))
    }

    // ============================================================ 死局局面

    @Test
    fun `满棋盘对角线构成死局`() {
        // 2x2 满盘：两个 A 在对角、两个 B 在对角。
        // 任一对都需要「出棋盘→沿外圈→进入目标列」共 3 个拐点，超出 BR-01 上限。
        val board = boardOf(
            "AB",
            "BA",
        )
        assertFalse(DeadlockDetector.hasMove(board))
        assertNull(DeadlockDetector.findMove(board))
    }

    @Test
    fun `少于两张牌时不存在可行步`() {
        assertNull("空棋盘不应有可行步", DeadlockDetector.findMove(boardOf("..", "..")))
        assertNull("只剩一张牌时不应有可行步", DeadlockDetector.findMove(boardOf("A.", "..")))
    }

    @Test
    fun `每种图案只剩一张时是死局`() {
        val board = boardOf("ABCD")
        assertFalse(DeadlockDetector.hasMove(board))
    }

    // ============================================================ 返回值的正确性

    @Test
    fun `findMove 返回的两张牌确实可连通且路径合法`() {
        val generator = BoardGenerator(Random(11L))
        for (config in LevelCatalog.levels) {
            val board = generator.generate(config)
            val move = DeadlockDetector.findMove(board) ?: continue

            assertEquals("两张牌类型应相同", move.first.type, move.second.type)
            assertTrue("两张牌不应是同一张", move.first.id != move.second.id)
            assertEquals("路径起点应为 first", move.first.position, move.path.from)
            assertEquals("路径终点应为 second", move.second.position, move.path.to)
            assertTrue("拐点数不应超过 2", move.path.turns <= PathFinder.MAX_TURNS)

            // 用 PathFinder 独立复算一次，确认该对确实连通
            assertNotNull(
                "findMove 返回的牌对必须能被 PathFinder 判定为连通",
                PathFinder.find(board, move.first.position, move.second.position),
            )
        }
    }

    // ============================================================ 交叉验证

    @Test
    fun `hasMove 与穷举全部同类型牌对的结果一致`() {
        val generator = BoardGenerator(Random(20260913L))
        var checkedBoards = 0

        for (config in listOf(
            LevelCatalog.configOf(1),
            LevelCatalog.configOf(5),
            LevelCatalog.configOf(10),
        )) {
            repeat(3) { round ->
                var board = generator.generate(config)
                // 逐对消除，覆盖从满盘到稀疏的各种局面
                while (board.remainingCount >= 2) {
                    assertEquals(
                        "第 ${config.level} 关第 $round 轮、剩余 ${board.remainingCount} 张时判定不一致",
                        bruteForceHasMove(board),
                        DeadlockDetector.hasMove(board),
                    )
                    checkedBoards++

                    val tiles = board.remainingTiles()
                    val first = tiles.first()
                    val partner = tiles.firstOrNull {
                        it.id != first.id && it.type == first.type
                    } ?: break
                    board = board.without(first.row, first.col)
                        .without(partner.row, partner.col)
                }
            }
        }

        assertTrue("应实际比对到足够多的棋盘状态", checkedBoards > 200)
    }

    /**
     * 独立实现：不依赖 [DeadlockDetector] 的分组与提前返回，直接穷举所有同类型牌对。
     * 用来在测试内部交叉验证被测实现的答案。
     */
    private fun bruteForceHasMove(board: Board): Boolean {
        val tiles = board.remainingTiles()
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                if (tiles[i].type != tiles[j].type) continue
                if (PathFinder.find(board, tiles[i].position, tiles[j].position) != null) {
                    return true
                }
            }
        }
        return false
    }
}
