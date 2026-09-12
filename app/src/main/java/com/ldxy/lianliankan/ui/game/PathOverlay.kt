package com.ldxy.lianliankan.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.ldxy.lianliankan.domain.model.Position

/**
 * 连线绘制（SRS FR-13.5）：Canvas 绘制带圆角折线接头的渐变连线。
 *
 * ### 坐标系
 * 画布尺寸是 `(cols + 2) × (rows + 2)` 个格子，棋盘左上角的牌位于 `(1, 1)`。
 * 这样外圈坐标（`row = -1` 或 `col = -1` / `= cols`）也落在画布内 ——
 * 与 SRS 9.1「棋盘四周扩展一圈虚拟空白」的模型一致，绕外侧的路径才有地方可画。
 *
 * @param points 来自 `PathFinder` 的完整路径点序列（SRS FR-5.6）。
 * @param progress 连线生长进度 0..1，驱动约 150ms 的绘制动画（开发计划 M6-5）。
 */
@Composable
fun PathOverlay(
    points: List<Position>,
    cellSize: Dp,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }

    Canvas(modifier = modifier) {
        if (points.size < 2 || progress <= 0f) return@Canvas

        // 格子坐标 → 画布像素中心；+1 格是因为棋盘整体偏移了一圈
        fun centerOf(position: Position) = Offset(
            x = (position.col + 1.5f) * cellPx,
            y = (position.row + 1.5f) * cellPx,
        )

        val full = Path().apply {
            val start = centerOf(points.first())
            moveTo(start.x, start.y)
            for (index in 1 until points.size) {
                val next = centerOf(points[index])
                lineTo(next.x, next.y)
            }
        }

        val measure = PathMeasure().apply { setPath(full, forceClosed = false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, startWithMoveTo = true)

        val stroke = Stroke(
            width = cellPx * 0.14f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        // 外发光：更粗、更透明的一层垫在下面
        drawPath(
            path = drawn,
            color = colors.tertiary.copy(alpha = 0.35f),
            style = Stroke(
                width = cellPx * 0.30f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 主体渐变
        drawPath(
            path = drawn,
            brush = Brush.linearGradient(
                colors = listOf(colors.tertiary, colors.primary),
                start = centerOf(points.first()),
                end = centerOf(points.last()),
            ),
            style = stroke,
        )
    }
}
