package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.model.Tile
import kotlin.random.Random

/**
 * 洗牌（SRS FR-3.4 / FR-6.2 / UC-05）。
 *
 * 只重排**未消除**的牌：已消除的格子保持为空，牌数与图案分布完全不变
 * （SRS M3-4，也是 FR-3.1「可完全消除」得以维持的前提）。
 *
 * ### 保证洗牌后有解
 * 先做随机重排，最多尝试 [maxAttempts] 次，每次用 [DeadlockDetector] 校验；
 * 全部失败则回退到**成对重排**（[placeGuaranteedSolvable]），由构造保证一定有解。
 *
 * 该回退策略的正确性可证：设 `S` 为未消除牌所在的格子集合。
 * 1. 若某一行里 `S` 有 ≥2 个格子，取其中列号相邻的两个 `p`、`q` ——
 *    二者之间没有 `S` 的格子，而「不属于 `S` 的格子」在棋盘上必然为空，
 *    故 `p → q` 直线畅通，0 拐点即可连通。
 * 2. 同列同理。
 * 3. 否则每一行、每一列至多含 1 个 `S` 格子。任取两格 `p`、`q`，
 *    拐点候选 `c = (p.row, q.col)` 落在 `p` 所在行上，而该行唯一的 `S` 格子是 `p`；
 *    又因 `q.col ≠ p.col` 故 `c ≠ p`，所以 `c ∉ S` 必为空；
 *    同理两段连线所在的行/列除 `p`、`q` 外均无 `S` 格子，路径畅通，1 拐点可连通。
 *
 * 于是只要还有 ≥2 张牌，就一定能摆出一个存在可行步的局面。
 *
 * [random] 可注入以便测试复现。
 */
class ShuffleService(
    private val random: Random = Random.Default,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {

    init {
        require(maxAttempts >= 0) { "重试次数不得为负，实际为 $maxAttempts" }
    }

    /**
     * 重排棋盘上剩余的牌，返回**保证有解**的新棋盘（FR-3.4）。
     *
     * 剩余牌不足 2 张时（空盘或只剩一张）直接原样返回，因为此时不存在「一对牌」的概念。
     */
    fun shuffle(board: Board): Board {
        val remaining = board.remainingTiles()
        if (remaining.size < 2) return board
        val positions = remaining.map { it.position }

        repeat(maxAttempts) {
            val candidate = place(board, positions, remaining.shuffled(random))
            if (DeadlockDetector.hasMove(candidate)) return candidate
        }
        return placeGuaranteedSolvable(board, positions, remaining)
    }

    /** 把 [tiles] 依次放到 [positions] 上，其余格子保持为空。 */
    private fun place(board: Board, positions: List<Position>, tiles: List<Tile>): Board {
        require(positions.size == tiles.size) {
            "位置数 ${positions.size} 与牌数 ${tiles.size} 不一致"
        }
        val placed = ArrayList<Tile>(tiles.size)
        for (index in tiles.indices) {
            placed += tiles[index].movedTo(positions[index].row, positions[index].col)
        }
        return Board.of(board.rows, board.cols, placed)
    }

    /**
     * 成对重排：把某一种图案的两张牌放到一对「必然可连通」的格子上，
     * 其余牌随意落位。用于随机重排始终失败时的兜底（见类注释的证明）。
     */
    private fun placeGuaranteedSolvable(
        board: Board,
        positions: List<Position>,
        tiles: List<Tile>,
    ): Board {
        val pair = findGuaranteedConnectablePair(positions)
            ?: return place(board, positions, tiles)
        val (firstPosition, secondPosition) = pair

        // FR-3.1 保证每类图案张数为偶数，故必有某一类至少有 2 张。
        val sameType = tiles.groupBy { it.type }.values.firstOrNull { it.size >= 2 }
            ?: return place(board, positions, tiles)

        val anchorFirst = sameType[0]
        val anchorSecond = sameType[1]
        val anchorIds = setOf(anchorFirst.id, anchorSecond.id)
        val others = tiles.filter { it.id !in anchorIds }
        val leftoverPositions = positions.filter {
            it != firstPosition && it != secondPosition
        }

        val placed = ArrayList<Tile>(tiles.size)
        placed += anchorFirst.movedTo(firstPosition.row, firstPosition.col)
        placed += anchorSecond.movedTo(secondPosition.row, secondPosition.col)
        for (index in leftoverPositions.indices) {
            placed += others[index].movedTo(leftoverPositions[index].row, leftoverPositions[index].col)
        }
        return Board.of(board.rows, board.cols, placed)
    }

    /**
     * 在 [positions] 中找一对「无论其余牌怎么摆都必然可连通」的格子。
     *
     * 判据见类注释：先找同行/同列中相邻的两格（0 拐点），
     * 否则任取两格走对角（1 拐点）。
     */
    private fun findGuaranteedConnectablePair(
        positions: List<Position>,
    ): Pair<Position, Position>? {
        if (positions.size < 2) return null

        for (rowPositions in positions.groupBy { it.row }.values) {
            if (rowPositions.size >= 2) {
                val sorted = rowPositions.sortedBy { it.col }
                return sorted[0] to sorted[1]
            }
        }
        for (colPositions in positions.groupBy { it.col }.values) {
            if (colPositions.size >= 2) {
                val sorted = colPositions.sortedBy { it.row }
                return sorted[0] to sorted[1]
            }
        }
        return positions[0] to positions[1]
    }

    companion object {
        /** SRS FR-3.4 要求的「有限次重试」上限。 */
        const val DEFAULT_MAX_ATTEMPTS: Int = 50
    }
}
