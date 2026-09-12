package com.ldxy.lianliankan.domain.path

import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.Position

/**
 * 连通判定（SRS FR-5）。
 *
 * 判定两张牌之间是否存在**拐点数 ≤ 2** 的可行折线路径，并给出完整路径点序列供 UI 绘制。
 *
 * ### 外侧虚拟空白区
 * SRS FR-3.5 / 9.1 要求允许路径绕行至棋盘外侧。这里不真的构造 `(rows+2) × (cols+2)`
 * 的扩展棋盘，而是把「棋盘外围一圈」表达为坐标范围 `row ∈ -1..rows`、`col ∈ -1..cols`：
 * 该范围内但落在棋盘外的格子在 [Board.isPassable] 中恒为可通行，超出这一圈则不可达。
 * 一圈的宽度足以表达「绕行 + 至多 2 个拐点」的全部路径。
 *
 * ### 与本类无关的规则
 * 本类只做**几何连通**，不检查两张牌的图案类型是否相同。SRS FR-5.1 的「同类型牌」是调用方的
 * 前置条件：M5 的 `GameSession` 先比较类型再调用本类，M3 的 `DeadlockDetector` 先按类型分组。
 * 这样可以把「类型规则」集中在一处，避免重复实现。
 *
 * 纯函数式：不持有状态、不修改 [Board]，可在 JVM 上直接单测（FR-5.7）。
 */
object PathFinder {

    /** SRS BR-01：拐点数上限。 */
    const val MAX_TURNS: Int = 2

    /**
     * 在 [a] 与 [b] 之间寻找拐点数最少的可行路径；不存在则返回 `null`。
     *
     * 按 0 → 1 → 2 拐点的顺序尝试，因此返回的路径拐点数是最小的。
     * 非法输入（同一坐标、越界、该位置没有牌）一律安全返回 `null`，不抛异常（NFR-2.4）。
     */
    fun find(board: Board, a: Position, b: Position): PathResult? {
        if (a == b) return null
        if (!board.contains(a) || !board.contains(b)) return null
        if (board.tileAt(a) == null || board.tileAt(b) == null) return null

        straightPath(board, a, b)?.let { return it }
        oneTurnPath(board, a, b)?.let { return it }
        return twoTurnPath(board, a, b)
    }

    /** [a] 与 [b] 是否可连通。 */
    fun hasPath(board: Board, a: Position, b: Position): Boolean = find(board, a, b) != null

    // ---------------------------------------------------------------- 0 拐点

    /** SRS FR-5.2：同行或同列，且两牌之间的格子全部为空。 */
    private fun straightPath(board: Board, a: Position, b: Position): PathResult? {
        if (a.row != b.row && a.col != b.col) return null
        if (!isLineClear(board, a, b)) return null
        return PathResult(listOf(a, b))
    }

    // ---------------------------------------------------------------- 1 拐点

    /** SRS FR-5.3：L 形，两个候选拐点任一可行即可。 */
    private fun oneTurnPath(board: Board, a: Position, b: Position): PathResult? {
        val candidates = listOf(Position(a.row, b.col), Position(b.row, a.col))
        for (corner in candidates) {
            // 拐点必须为空。a、b 本身是牌，isFree 会返回 false，
            // 因此「拐点退化到端点」的情形自动排除（那些情形已由 0 拐点覆盖）。
            if (!isFree(board, corner)) continue
            if (!isLineClear(board, a, corner)) continue
            if (!isLineClear(board, corner, b)) continue
            return PathResult(normalize(listOf(a, corner, b)))
        }
        return null
    }

    // ---------------------------------------------------------------- 2 拐点

    /**
     * SRS FR-5.4 / FR-5.5：Z / U 形与绕外侧。
     *
     * 两条线段方向交替，即 `A →X` 与 `Y →B` 平行、`X →Y` 与二者垂直。
     * 于是做法是：沿 [a] 的行/列方向逐个取可达的空格 X，再从 X 沿垂直方向逐个取可达的空格 Y，
     * 最后检查 Y 与 [b] 是否直线可达。
     */
    private fun twoTurnPath(board: Board, a: Position, b: Position): PathResult? {
        for ((dr, dc) in DIRECTIONS) {
            for (x in freeCellsAlong(board, a, dr, dc)) {
                for ((er, ec) in perpendicular(dr, dc)) {
                    for (y in freeCellsAlong(board, x, er, ec)) {
                        if (!isLineClear(board, y, b)) continue
                        val path = normalize(listOf(a, x, y, b))
                        if (path.size - 2 <= MAX_TURNS) return PathResult(path)
                    }
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- 几何辅助

    /**
     * 该格能否作为路径经过或作为拐点。
     *
     * 只有「棋盘内且无牌」或「棋盘外但仍在最外围那一圈」才算可用；
     * 再往外一层视为不存在，扫描自然终止。
     */
    private fun isFree(board: Board, row: Int, col: Int): Boolean {
        if (row < -1 || row > board.rows) return false
        if (col < -1 || col > board.cols) return false
        return board.isPassable(row, col)
    }

    private fun isFree(board: Board, position: Position): Boolean =
        isFree(board, position.row, position.col)

    /**
     * 从 [origin] 沿 `(dr, dc)` 逐格前进，收集连续的可空格子，遇到不可空格子即停止。
     *
     * 返回的格子均可用作拐点；不含 [origin] 自身。
     */
    private fun freeCellsAlong(
        board: Board,
        origin: Position,
        dr: Int,
        dc: Int,
    ): List<Position> {
        val result = ArrayList<Position>()
        var row = origin.row + dr
        var col = origin.col + dc
        while (isFree(board, row, col)) {
            result += Position(row, col)
            row += dr
            col += dc
        }
        return result
    }

    /**
     * [p] 与 [q] 之间（不含两端）是否全部可通行。要求两点同行或同列。
     */
    private fun isLineClear(board: Board, p: Position, q: Position): Boolean {
        if (p.row == q.row) {
            for (col in (minOf(p.col, q.col) + 1)..(maxOf(p.col, q.col) - 1)) {
                if (!board.isPassable(p.row, col)) return false
            }
            return true
        }
        if (p.col == q.col) {
            for (row in (minOf(p.row, q.row) + 1)..(maxOf(p.row, q.row) - 1)) {
                if (!board.isPassable(row, p.col)) return false
            }
            return true
        }
        return false
    }

    /** 去掉共线的中间点，使相邻两点方向必定发生变化，从而 [PathResult.turns] 与点序列一致。 */
    private fun normalize(points: List<Position>): List<Position> {
        if (points.size <= 2) return points

        val result = ArrayList<Position>(points.size)
        result += points.first()
        for (i in 1 until points.size - 1) {
            val previous = result.last()
            val current = points[i]
            val next = points[i + 1]
            val collinear = (previous.row == current.row && current.row == next.row) ||
                (previous.col == current.col && current.col == next.col)
            if (!collinear) result += current
        }
        result += points.last()
        return result
    }

    private fun perpendicular(dr: Int, dc: Int): List<Pair<Int, Int>> =
        if (dr == 0) listOf(-1 to 0, 1 to 0) else listOf(0 to -1, 0 to 1)

    private val DIRECTIONS = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
}
