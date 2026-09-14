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
import com.ldxy.lianliankan.ui.theme.LocalTileSkin

/**
 * 牌面视觉（V1.2 的 J-1）。
 *
 * **只认一个图案字符与一组状态标志，完全不依赖 `Tile`** —— 这样设置页的皮肤预览可以
 * 直接复用它渲染示例牌面，不必伪造 `Tile` 领域对象；预览与棋盘共用同一段视觉代码，
 * 也就不会出现「预览好看、实际不一样」。
 *
 * 这里承载**全部**牌面视觉（外发光、卡片底、描边、图案、按压反馈）；
 * [TileView] 只是一层把 `Tile` 翻译成 [face] 与状态标志的薄适配层。
 *
 * 状态标志之所以是入参而非从 `Tile` 推导，正是因为预览场景没有 `Tile`。
 */
@Composable
fun TileFaceBox(
    face: String,
    cellSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    isHinted: Boolean = false,
    isRejected: Boolean = false,
    alpha: Float = 1f,
    extraScale: Float = 1f,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val palette = MaterialTheme.colorScheme
    val container = when {
        isRejected -> palette.errorContainer
        isSelected -> palette.primaryContainer
        isHinted -> palette.tertiaryContainer
        else -> palette.surfaceVariant
    }
    val content = when {
        isRejected -> palette.onErrorContainer
        isSelected -> palette.onPrimaryContainer
        isHinted -> palette.onTertiaryContainer
        else -> palette.onSurfaceVariant
    }
    val accent = when {
        isRejected -> palette.error
        isSelected -> palette.primary
        isHinted -> palette.tertiary
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
            Text(
                text = face,
                fontSize = with(LocalDensity.current) { (cellSize * 0.42f).toSp() },
                fontWeight = FontWeight.Medium,
                color = content,
                modifier = Modifier.semantics { contentDescription = face },
            )
        }
    }
}

/**
 * 棋盘上的一张牌（SRS FR-13.3 / FR-13.4 / FR-13.7）。
 *
 * 只做两件事：把 `Tile.state` 翻译成状态标志、从 [LocalTileSkin] 解析出图案字符，
 * 然后交给 [TileFaceBox]。所有视觉都在后者。
 *
 * 图案取自当前皮肤，因此设置页切换皮肤后棋盘会立即跟着变（SRS FR-11.6）。
 *
 * 语义描述只用图案字符本身，**不暴露坐标** —— 读屏时泄露行列信息对这类游戏没有帮助，
 * 而图案字符是玩家本来就能看到的内容。
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
    TileFaceBox(
        face = LocalTileSkin.current.faceAt(tile.type),
        cellSize = cellSize,
        modifier = modifier,
        enabled = enabled,
        isSelected = tile.state == TileState.SELECTED,
        isHinted = tile.state == TileState.HINTED,
        isRejected = isRejected,
        alpha = alpha,
        extraScale = extraScale,
        onClick = onClick,
    )
}
