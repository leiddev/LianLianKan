package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.path.PathFinder

/**
 * 死局检测（SRS FR-6.1）：判断棋盘上是否还存在任意一对可连通的同类型牌。
 *
 * 每次成功消除后调用一次（见 SRS UC-03 主流程第 4 步）；若返回 `false`
 * 即为死局，需自动洗牌（FR-6.2）。同时也为提示道具提供「找一步」的能力（FR-9.1）。
 *
 * 实现按图案类型分组后组内两两试连通，并在**找到第一对时立即返回**，
 * 因此正常局面下代价很低；只有在真正死局时才会遍历全部同类型牌对。
 * 8×12 规模下即使全量遍历也远低于实时要求（SRS 9.1 已说明复杂度可忽略）。
 */
object DeadlockDetector {

    /** 棋盘上是否还存在可行的一步。 */
    fun hasMove(board: Board): Boolean = findMove(board) != null

    /**
     * 找出任意一步可行操作；死局返回 `null`。
     *
     * 返回结果稳定可复现（按行优先顺序扫描，取第一对可连通的同类型牌）。
     */
    fun findMove(board: Board): Move? {
        val tiles = board.remainingTiles()
        if (tiles.size < 2) return null

        for (group in tiles.groupBy { it.type }.values) {
            if (group.size < 2) continue
            for (i in group.indices) {
                val first = group[i]
                for (j in i + 1 until group.size) {
                    val second = group[j]
                    val path = PathFinder.find(board, first.position, second.position) ?: continue
                    return Move(first = first, second = second, path = path)
                }
            }
        }
        return null
    }
}
