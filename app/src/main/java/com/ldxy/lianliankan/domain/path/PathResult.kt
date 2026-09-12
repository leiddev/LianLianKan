package com.ldxy.lianliankan.domain.path

import com.ldxy.lianliankan.domain.model.Position

/**
 * 一次成功的连通判定结果（SRS FR-5.6）。
 *
 * [points] 是完整的路径点序列：首点为起点牌坐标、末点为终点牌坐标，
 * 中间元素即折线的拐点。M6 的 `PathOverlay` 直接用它在 Canvas 上绘制连线。
 *
 * 不变量：相邻两点必定同行或同列（即每一段都是水平或垂直的直线段），
 * 且相邻两点不重合 —— 由 [PathFinder] 在 [normalize] 阶段保证。
 */
data class PathResult(
    val points: List<Position>,
) {
    init {
        require(points.size >= 2) { "路径至少包含起点与终点两个点，实际为 ${points.size} 个" }
    }

    /** 拐点数。路径点数减 2 即中间拐点个数（SRS BR-01：须 ≤ 2）。 */
    val turns: Int get() = points.size - 2

    val from: Position get() = points.first()

    val to: Position get() = points.last()

    /** 中间拐点序列。 */
    val corners: List<Position> get() = points.subList(1, points.size - 1)

    override fun toString(): String =
        "PathResult(turns=$turns, points=${points.joinToString(" -> ") { "(${it.row},${it.col})" }})"
}
