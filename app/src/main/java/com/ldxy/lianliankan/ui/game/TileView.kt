package com.ldxy.lianliankan.ui.game

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ldxy.lianliankan.domain.model.Tile
import com.ldxy.lianliankan.domain.model.TileState

/**
 * 单张牌（SRS FR-13.3 / FR-13.4 / FR-13.7）。
 *
 * - 圆角卡片 + 按压缩放反馈（FR-13.3）；
 * - 选中态：主色调底 + 描边 + 外发光（FR-13.4）；
 * - 提示态：第三色调底 + 描边（FR-9.1 的高亮）；
 * - 错误态：红色调（抖动由调用方通过 [modifier] 施加，见 `BoardView`）；
 * - 图案用 emoji 文本渲染，字号随格子边长缩放（FR-13.7）；
 * - [alpha] / [extraScale] 供消除动画（缩放 + 淡出，FR-13.6）驱动。
 *
 * 牌面元素不参与语义树的具体图案信息，只暴露坐标描述，避免读屏时泄露图案导致「作弊」，
 * 同时保留可定位性。
 */
@Composable
fun TileView(
    tile: Tile,
    cellSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRejected: Boolean = false,
    alpha: Float = 1f,
    extraScale: Float = 1f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val isSelected = tile.state == TileState.SELECTED
    val isHinted = tile.state == TileState.HINTED

    val colors = MaterialTheme.colorScheme
    val container = when {
        isRejected -> colors.errorContainer
        isSelected -> colors.primaryContainer
        isHinted -> colors.tertiaryContainer
        else -> colors.surfaceVariant
    }
    val content = when {
        isRejected -> colors.onErrorContainer
        isSelected -> colors.onPrimaryContainer
        isHinted -> colors.onTertiaryContainer
        else -> colors.onSurfaceVariant
    }
    val accent = when {
        isRejected -> colors.error
        isSelected -> colors.primary
        isHinted -> colors.tertiary
        else -> Color.Transparent
    }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tilePressScale",
    )
    val emphasis = if (isSelected) 1.08f else 1f

    val corner = cellSize * 0.22f
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .size(cellSize)
            .scale(pressScale * emphasis * extraScale)
            .alpha(alpha)
            .padding(cellSize * 0.045f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 选中/提示/错误时先铺一层放大的同色底，形成外发光（SRS FR-13.4）
        if (accent != Color.Transparent) {
            Box(
                modifier = Modifier
                    .size(cellSize * 0.96f)
                    .background(
                        color = accent.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(corner * 1.35f),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(cellSize * 0.9f)
                .background(container, shape)
                .border(
                    width = if (accent == Color.Transparent) 0.dp else cellSize * 0.05f,
                    color = accent,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            Text(
                text = TileFaces.faceFor(tile.type),
                fontSize = with(density) { (cellSize * 0.42f).toSp() },
                fontWeight = FontWeight.Medium,
                color = content,
                modifier = Modifier.semantics {
                    contentDescription = "tile-${tile.row}-${tile.col}"
                },
            )
        }
    }
}
