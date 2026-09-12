package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.model.Tile
import com.ldxy.lianliankan.domain.path.PathResult

/**
 * 一次可行的消除操作：两张同类型且可连通的牌，以及它们之间的路径。
 *
 * 三个使用方：
 * - M3 的 `DeadlockDetector` 用它表达「棋盘上至少还存在一步」；
 * - M5 的 `GameSession` 用它实现提示道具（SRS FR-9.1）；
 * - M6 的 `PathOverlay` 用 [path] 绘制连线（SRS FR-4.6）。
 */
data class Move(
    val first: Tile,
    val second: Tile,
    val path: PathResult,
)
