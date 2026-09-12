package com.ldxy.lianliankan.domain.path

import com.ldxy.lianliankan.domain.at
import com.ldxy.lianliankan.domain.board.BoardGenerator
import com.ldxy.lianliankan.domain.boardOf
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.Position
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连通判定单测，覆盖开发计划 M2-7 的完整矩阵。
 *
 * 对应验收标准：AC-02（0 拐点）、AC-03（1 拐点）、AC-04（2 拐点）、AC-05（阻挡判定），
 * 以及 FR-5.5（绕外侧）、FR-5.6（完整路径序列）、BR-01（拐点数 ≤ 2）。
 */
class PathFinderTest {

    // ============================================================ 0 拐点（AC-02）

    @Test
    fun `AC-02 同行相邻两牌可连通且拐点数为 0`() {
        val board = boardOf("AA")
        val path = PathFinder.find(board, at(0, 0), at(0, 1))

        assertNotNull("相邻两牌必须可连通", path)
        assertEquals(0, path!!.turns)
        assertEquals(listOf(at(0, 0), at(0, 1)), path.points)
    }

    @Test
    fun `AC-02 同行之间隔空格可连通且拐点数为 0`() {
        val board = boardOf("A.A")
        val path = PathFinder.find(board, at(0, 0), at(0, 2))

        assertNotNull(path)
        assertEquals(0, path!!.turns)
        assertEquals(listOf(at(0, 0), at(0, 2)), path.points)
    }

    @Test
    fun `AC-02 同列之间隔空格可连通且拐点数为 0`() {
        val board = boardOf(
            "A",
            ".",
            "A",
        )
        val path = PathFinder.find(board, at(0, 0), at(2, 0))

        assertNotNull(path)
        assertEquals(0, path!!.turns)
        assertEquals(listOf(at(0, 0), at(2, 0)), path.points)
    }

    // ============================================================ 1 拐点（AC-03）

    @Test
    fun `AC-03 仅上方拐点可用时走 L 形且拐点数为 1`() {
        val board = boardOf(
            "A..",
            "XX.",
            "XXA",
        )
        val path = PathFinder.find(board, at(0, 0), at(2, 2))

        assertNotNull(path)
        assertEquals(1, path!!.turns)
        assertEquals(at(0, 2), path.corners.single())
    }

    @Test
    fun `AC-03 仅下方拐点可用时走 L 形且拐点数为 1`() {
        val board = boardOf(
            "AXX",
            ".XX",
            "..A",
        )
        val path = PathFinder.find(board, at(0, 0), at(2, 2))

        assertNotNull(path)
        assertEquals(1, path!!.turns)
        assertEquals(at(2, 0), path.corners.single())
    }

    @Test
    fun `AC-03 两个候选拐点都可用时取先试到的那个`() {
        val board = boardOf(
            "A..",
            "...",
            "..A",
        )
        val path = PathFinder.find(board, at(0, 0), at(2, 2))

        assertNotNull(path)
        assertEquals(1, path!!.turns)
        // 候选顺序为 (a.row, b.col) 在前
        assertEquals(at(0, 2), path.corners.single())
    }

    // ============================================================ 2 拐点（AC-04）

    @Test
    fun `AC-04 Z 形路径可连通且拐点数为 2`() {
        val board = boardOf(
            "A.X",
            "X.X",
            "X.A",
        )
        val path = PathFinder.find(board, at(0, 0), at(2, 2))

        assertNotNull(path)
        assertEquals(2, path!!.turns)
        assertEquals(listOf(at(0, 0), at(0, 1), at(2, 1), at(2, 2)), path.points)
    }

    @Test
    fun `AC-04 U 形路径可连通且拐点数为 2`() {
        val board = boardOf(
            ".X.",
            "AXA",
            ".X.",
        )
        val path = PathFinder.find(board, at(1, 0), at(1, 2))

        assertNotNull(path)
        assertEquals(2, path!!.turns)
    }

    @Test
    fun `AC-04 棋盘四角两两可连通`() {
        val board = boardOf(
            "A..B",
            "....",
            "....",
            "B..A",
        )

        val mainDiagonal = PathFinder.find(board, at(0, 0), at(3, 3))
        val antiDiagonal = PathFinder.find(board, at(0, 3), at(3, 0))

        assertNotNull("主对角线两角应可连通", mainDiagonal)
        assertNotNull("副对角线两角应可连通", antiDiagonal)
        assertTrue(mainDiagonal!!.turns <= 2)
        assertTrue(antiDiagonal!!.turns <= 2)
    }

    // ============================================================ 绕外侧（FR-5.5）

    @Test
    fun `FR-5-5 同行被阻挡时绕棋盘外侧连通`() {
        // 只有一行，直线被中间的 B 挡住，唯一出路是从棋盘上方（row = -1）绕行
        val board = boardOf("ABA")
        val path = PathFinder.find(board, at(0, 0), at(0, 2))

        assertNotNull("应能通过外圈绕行连通", path)
        assertEquals(2, path!!.turns)
        assertTrue("路径应经过棋盘外侧", path.points.any { it.row < 0 || it.col < 0 || it.row >= board.rows || it.col >= board.cols })
    }

    @Test
    fun `FR-5-5 贴左右边界的同列两牌可绕外侧连通`() {
        val board = boardOf(
            "AXA",
            "XXX",
        )
        // (0,0) 与 (0,2)：同一行被 X 挡住，且下方被 X 封死，只能从上方外圈走
        val path = PathFinder.find(board, at(0, 0), at(0, 2))

        assertNotNull(path)
        assertEquals(2, path!!.turns)
    }

    // ============================================================ 阻挡判定（AC-05 / BR-01）

    @Test
    fun `AC-05 BR-01 被完全包围的牌判定为不可连通`() {
        val board = boardOf(
            "BXXX",
            "XXAX",
            "XXXX",
            "XXXX",
        )
        // A 位于 (1,2)，四邻均为牌，任何方向都无法迈出第一步
        assertNull(PathFinder.find(board, at(1, 2), at(0, 0)))
    }

    @Test
    fun `BR-01 满棋盘对角线需要 3 个拐点时判定为不可连通`() {
        // 2x2 满棋盘，两个 A 位于对角；B 占住另两格。
        // 绕外圈需要「上→沿外圈→进入目标列」共 3 个拐点，超出 BR-01 上限。
        val board = boardOf(
            "AB",
            "BA",
        )
        assertNull(
            "对角线且正交邻居被占时应判定不通",
            PathFinder.find(board, at(0, 0), at(1, 1)),
        )
    }

    @Test
    fun `AC-05 对照组 - 移除一个阻挡牌后同一对牌即可连通`() {
        // 与上一个用例坐标完全相同，只把 (0,1) 清空。
        // 连通性随之恢复，证明此前判定为不通确实是「被牌阻挡」所致，而非实现缺陷。
        val blocked = boardOf(
            "AB",
            "BA",
        )
        val opened = boardOf(
            "A.",
            "BA",
        )

        assertNull(PathFinder.find(blocked, at(0, 0), at(1, 1)))
        val path = PathFinder.find(opened, at(0, 0), at(1, 1))
        assertNotNull("移除阻挡后应恢复连通", path)
        assertEquals(1, path!!.turns)
        assertEquals(at(0, 1), path.corners.single())
    }

    @Test
    fun `AC-05 阻挡使直线路径失效 - 由 0 拐点变为 2 拐点绕行`() {
        // 同一对坐标，只差中间那一格：
        //   "A.A" —— 中间为空，走直线（0 拐点）
        //   "ABA" —— 中间被牌占据，直线作废，只能从棋盘外侧绕行（2 拐点）
        val clear = boardOf("A.A")
        val blocked = boardOf("ABA")

        val straight = PathFinder.find(clear, at(0, 0), at(0, 2))
        assertNotNull(straight)
        assertEquals("中间为空时应走直线", 0, straight!!.turns)

        val detour = PathFinder.find(blocked, at(0, 0), at(0, 2))
        assertNotNull("被阻挡时不应直接判不通，而应改走外侧绕行", detour)
        assertEquals("被阻挡后不可能仍是 0 拐点", 2, detour!!.turns)
        assertTrue(
            "绕行路径不得经过被占据的 (0, 1)",
            detour.points.none { it == at(0, 1) },
        )
    }

    // ============================================================ 非法输入（NFR-2.4）

    @Test
    fun `同一坐标返回 null`() {
        val board = boardOf("A.A")
        assertNull(PathFinder.find(board, at(0, 0), at(0, 0)))
    }

    @Test
    fun `越界坐标安全返回 null 而不抛异常`() {
        val board = boardOf(
            "A.",
            ".A",
        )
        assertNull(PathFinder.find(board, at(-1, 0), at(1, 1)))
        assertNull(PathFinder.find(board, at(0, 0), at(5, 5)))
        assertNull(PathFinder.find(board, at(0, 99), at(0, 0)))
    }

    @Test
    fun `空格位置返回 null`() {
        val board = boardOf("A.A")
        assertNull(PathFinder.find(board, at(0, 0), at(0, 1)))
        assertNull(PathFinder.find(board, at(0, 1), at(0, 2)))
    }

    @Test
    fun `已消除的牌不再参与判定`() {
        val board = boardOf("AA")
        assertNotNull(PathFinder.find(board, at(0, 0), at(0, 1)))

        val afterRemoval = board.without(0, 0)
        assertNull(PathFinder.find(afterRemoval, at(0, 0), at(0, 1)))
    }

    // ============================================================ 类型规则归属

    @Test
    fun `本类只做几何连通 - 图案类型是否相同由调用方判断`() {
        // SRS FR-5.1 的「同类型牌」是调用方的前置条件（见 GameSession / DeadlockDetector）。
        // 此处显式固定这一契约，避免日后误加类型判断导致职责重复。
        val board = boardOf("AB")
        val path = PathFinder.find(board, at(0, 0), at(0, 1))

        assertNotNull("类型不同的相邻两牌在几何上仍然连通", path)
        assertEquals(0, path!!.turns)

        assertEquals(
            "两张牌的图案类型确实不同",
            false,
            board.tileAt(0, 0)!!.type == board.tileAt(0, 1)!!.type,
        )
    }

    // ============================================================ 路径结构（FR-5.6）

    @Test
    fun `FR-5-6 真实棋盘上所有可连通对都满足路径结构不变量`() {
        val generator = BoardGenerator(Random(20260912L))
        val configs = listOf(
            LevelCatalog.configOf(1),
            LevelCatalog.configOf(6),
            LevelCatalog.configOf(10),
        )

        var checkedPairs = 0
        var checkedPaths = 0

        for (config in configs) {
            repeat(3) { round ->
                val board = generator.generate(config)
                val tiles = board.remainingTiles()
                for (i in tiles.indices) {
                    for (j in i + 1 until tiles.size) {
                        val first = tiles[i]
                        val second = tiles[j]
                        if (first.type != second.type) continue
                        checkedPairs++

                        val path = PathFinder.find(board, first.position, second.position)
                            ?: continue
                        checkedPaths++
                        assertPathInvariants(
                            board = board,
                            from = first.position,
                            to = second.position,
                            path = path,
                            where = "第 ${config.level} 关第 $round 轮 ($first)-($second)",
                        )
                    }
                }
            }
        }

        assertTrue("应实际检查到一定数量的同类型牌对", checkedPairs > 100)
        assertTrue("随机棋盘上应存在大量可连通对，否则测试无意义", checkedPaths > 50)
    }

    @Test
    fun `返回的路径拐点数不大于 2 且为同类型对中的最小拐点数`() {
        val board = boardOf(
            "A.X",
            "X.X",
            "X.A",
        )
        val path = PathFinder.find(board, at(0, 0), at(2, 2))

        assertNotNull(path)
        assertEquals("该布局不存在 0 或 1 拐点路径，应返回 2 拐点路径", 2, path!!.turns)
    }

    // ============================================================ 断言辅助

    private fun assertPathInvariants(
        board: Board,
        from: Position,
        to: Position,
        path: PathResult,
        where: String,
    ) {
        assertEquals("$where：起点不符", from, path.from)
        assertEquals("$where：终点不符", to, path.to)
        assertTrue("$where：拐点数 ${path.turns} 超过 BR-01 上限", path.turns <= PathFinder.MAX_TURNS)
        assertEquals("$where：turns 与点数不一致", path.points.size - 2, path.turns)

        for (i in 1 until path.points.size) {
            val previous = path.points[i - 1]
            val current = path.points[i]
            assertTrue("$where：相邻点重合 $current", previous != current)
            assertTrue(
                "$where：相邻点不共线 $previous -> $current",
                previous.row == current.row || previous.col == current.col,
            )
        }

        for (corner in path.corners) {
            assertTrue(
                "$where：拐点 $corner 被未消除的牌占据",
                board.isPassable(corner.row, corner.col),
            )
        }

        for (i in 1 until path.points.size) {
            val previous = path.points[i - 1]
            val current = path.points[i]
            if (previous.row == current.row) {
                for (col in (minOf(previous.col, current.col) + 1)..(maxOf(previous.col, current.col) - 1)) {
                    assertTrue(
                        "$where：线段 $previous -> $current 被 (${previous.row}, $col) 阻挡",
                        board.isPassable(previous.row, col),
                    )
                }
            } else {
                for (row in (minOf(previous.row, current.row) + 1)..(maxOf(previous.row, current.row) - 1)) {
                    assertTrue(
                        "$where：线段 $previous -> $current 被 ($row, ${previous.col}) 阻挡",
                        board.isPassable(row, previous.col),
                    )
                }
            }
        }
    }
}
