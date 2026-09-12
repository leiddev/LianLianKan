package com.ldxy.lianliankan.ui.game

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.model.Tile

/**
 * 棋盘容器（开发计划 M6-1）。
 *
 * 用 `Box` + 绝对偏移摆放每一张牌，而不是 `LazyVerticalGrid`：棋盘尺寸固定且最大 96 格，
 * 不需要懒加载，而绝对定位是绘制连线（[PathOverlay] 用的是同一套格子坐标）的前提。
 *
 * 牌的偏移用 [animateDpAsState] 过渡，因此洗牌时牌会平滑滑动而不是瞬间跳位。
 *
 * @param shakeOffset 错误反馈的抖动位移，由 `GameScreen` 驱动（SRS FR-4.4 / FR-4.5）。
 * @param rejectedPositions 需要标红并抖动的两张牌。
 * @param ghosts 已从棋盘移除、正在播放消除动画的牌（SRS FR-13.6）。
 */
@Composable
fun BoardView(
    board: Board,
    cellSize: Dp,
    enabled: Boolean,
    onTileClick: (Position) -> Unit,
    modifier: Modifier = Modifier,
    rejectedPositions: Set<Position> = emptySet(),
    shakeOffset: Dp = 0.dp,
    ghosts: List<GhostTile> = emptyList(),
) {
    Box(
        modifier = modifier.size(
            width = cellSize * board.cols,
            height = cellSize * board.rows,
        ),
    ) {
        for (row in 0 until board.rows) {
            for (col in 0 until board.cols) {
                val tile = board.tileAt(row, col) ?: continue
                key(tile.id) {
                    val position = Position(row, col)
                    val isRejected = position in rejectedPositions

                    val targetX = cellSize * col + if (isRejected) shakeOffset else 0.dp
                    val targetY = cellSize * row

                    val x by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = tween(durationMillis = SHIFT_MILLIS),
                        label = "tileX",
                    )
                    val y by animateDpAsState(
                        targetValue = targetY,
                        animationSpec = tween(durationMillis = SHIFT_MILLIS),
                        label = "tileY",
                    )

                    TileView(
                        tile = tile,
                        cellSize = cellSize,
                        enabled = enabled,
                        isRejected = isRejected,
                        onClick = { onTileClick(position) },
                        modifier = Modifier.offset(x = x, y = y),
                    )
                }
            }
        }

        // 消除动画期间的残影：牌已从 board 中移除，用快照渲染在原位置淡出缩小
        for (ghost in ghosts) {
            key("ghost-${ghost.tile.id}") {
                TileView(
                    tile = ghost.tile,
                    cellSize = cellSize,
                    enabled = false,
                    alpha = ghost.alpha,
                    extraScale = ghost.scale,
                    onClick = {},
                    modifier = Modifier.offset(
                        x = cellSize * ghost.tile.col,
                        y = cellSize * ghost.tile.row,
                    ),
                )
            }
        }
    }
}

/** 正在播放消除动画的牌。 */
data class GhostTile(
    val tile: Tile,
    val alpha: Float,
    val scale: Float,
)

/** 牌位移动画时长（洗牌时的滑动）。 */
private const val SHIFT_MILLIS = 220
