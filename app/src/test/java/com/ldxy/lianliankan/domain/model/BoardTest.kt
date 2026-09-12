package com.ldxy.lianliankan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Board] 的基础行为单测；连通判定相关的边界在 M2 覆盖。 */
class BoardTest {

    private fun tile(id: Int, type: Int, row: Int, col: Int) = Tile(id, type, row, col)

    @Test
    fun `empty 棋盘全部为空且已清盘`() {
        val board = Board.empty(rows = 2, cols = 3)
        assertEquals(6, board.tileCount)
        assertEquals(0, board.remainingCount)
        assertTrue(board.isCleared)
        assertNull(board.tileAt(0, 0))
    }

    @Test
    fun `of 按坐标落位并可读回`() {
        val board = Board.of(
            rows = 2,
            cols = 2,
            tiles = listOf(tile(0, type = 7, row = 0, col = 1), tile(1, type = 9, row = 1, col = 0)),
        )
        assertEquals(7, board.tileAt(0, 1)!!.type)
        assertEquals(9, board.tileAt(1, 0)!!.type)
        assertNull(board.tileAt(0, 0))
        assertEquals(2, board.remainingCount)
        assertFalse(board.isCleared)
    }

    @Test
    fun `越界坐标取牌返回 null 且视为可通行`() {
        val board = Board.of(rows = 1, cols = 1, tiles = listOf(tile(0, 0, 0, 0)))

        assertNull(board.tileAt(-1, 0))
        assertNull(board.tileAt(0, 1))
        assertNull(board.tileAt(5, 5))
        assertFalse(board.contains(-1, 0))
        assertFalse(board.contains(0, 1))

        // SRS FR-3.5：棋盘外侧为永久空白，可绕行连通
        assertTrue(board.isPassable(-1, 0))
        assertTrue(board.isPassable(0, 1))
        assertTrue(board.isPassable(0, -1))
        assertTrue(board.isPassable(1, 0))
    }

    @Test
    fun `棋盘内空格可通行而有牌不可通行`() {
        val board = Board.of(rows = 2, cols = 2, tiles = listOf(tile(0, 0, 0, 0)))
        assertFalse(board.isPassable(0, 0))
        assertTrue(board.isPassable(0, 1))
        assertTrue(board.isPassable(1, 1))
    }

    @Test
    fun `without 消除后不可通行且数量递减`() {
        val board = Board.of(
            rows = 1,
            cols = 3,
            tiles = listOf(tile(0, 0, 0, 0), tile(1, 0, 0, 1), tile(2, 0, 0, 2)),
        )
        val after = board.without(0, 1)

        assertEquals(3, board.remainingCount)
        assertEquals(2, after.remainingCount)
        assertNull(after.tileAt(0, 1))
        assertTrue(after.isPassable(0, 1))
        assertFalse(after.isCleared)
    }

    @Test
    fun `without 对已空格子返回自身`() {
        val board = Board.of(rows = 1, cols = 2, tiles = listOf(tile(0, 0, 0, 0)))
        assertSame(board, board.without(0, 1))
    }

    @Test
    fun `withTile 可切换选中态且不可变`() {
        val board = Board.of(rows = 1, cols = 1, tiles = listOf(tile(0, type = 3, row = 0, col = 0)))
        val selected = board.withTile(board.tileAt(0, 0)!!.withState(TileState.SELECTED))

        assertEquals(TileState.NORMAL, board.tileAt(0, 0)!!.state)
        assertEquals(TileState.SELECTED, selected.tileAt(0, 0)!!.state)
        assertEquals(3, selected.tileAt(0, 0)!!.type)
    }

    @Test
    fun `remainingCountsByType 按类型统计未消除牌`() {
        val board = Board.of(
            rows = 1,
            cols = 4,
            tiles = listOf(
                tile(0, type = 1, row = 0, col = 0),
                tile(1, type = 1, row = 0, col = 1),
                tile(2, type = 2, row = 0, col = 2),
                tile(3, type = 2, row = 0, col = 3),
            ),
        )
        assertEquals(mapOf(1 to 2, 2 to 2), board.remainingCountsByType())

        val after = board.without(0, 0).without(0, 2)
        assertEquals(mapOf(1 to 1, 2 to 1), after.remainingCountsByType())
    }

    @Test
    fun `全部消除后 isCleared 为真`() {
        var board = Board.of(
            rows = 1,
            cols = 2,
            tiles = listOf(tile(0, 0, 0, 0), tile(1, 0, 0, 1)),
        )
        board = board.without(0, 0).without(0, 1)
        assertTrue(board.isCleared)
        assertEquals(0, board.remainingCount)
    }

    @Test
    fun `equals 与 hashCode 具备值语义`() {
        val a = Board.of(rows = 2, cols = 2, tiles = listOf(tile(0, 5, 0, 0)))
        val b = Board.of(rows = 2, cols = 2, tiles = listOf(tile(0, 5, 0, 0)))
        val c = Board.of(rows = 2, cols = 2, tiles = listOf(tile(0, 6, 0, 0)))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `of 拒绝重复 id`() {
        Board.of(
            rows = 1,
            cols = 2,
            tiles = listOf(tile(0, 0, 0, 0), tile(0, 0, 0, 1)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `of 拒绝越界坐标`() {
        Board.of(rows = 1, cols = 1, tiles = listOf(tile(0, 0, row = 0, col = 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `of 拒绝同一坐标放多张牌`() {
        Board.of(
            rows = 1,
            cols = 1,
            tiles = listOf(tile(0, 0, 0, 0), tile(1, 0, 0, 0)),
        )
    }
}
