package com.ldxy.lianliankan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 统一圆角（SRS FR-13.8：整体风格协调统一）。
 *
 * 牌面自身的圆角在 `TileView` 中按格子边长等比计算（小尺寸下固定圆角会显得过圆），
 * 这里的形状用于按钮、卡片、弹窗等常规容器。
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 统一间距刻度（SRS FR-13.8）。
 *
 * 只保留 5 档，避免各屏幕各自写 `13.dp`、`17.dp` 这类随手值导致节奏混乱。
 */
internal object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 40.dp
}
