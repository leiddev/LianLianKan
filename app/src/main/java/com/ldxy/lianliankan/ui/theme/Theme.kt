package com.ldxy.lianliankan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush

/**
 * 应用主题入口（SRS FR-13.1：采用 Material 3 设计规范）。
 *
 * 深浅两套配色见 `Color.kt`，圆角与间距见 `Shape.kt`，皮肤见 `Skin.kt`。
 * 三态主题（跟随系统 / 浅色 / 深色）由调用方把 `ThemeMode` 解析成 [darkTheme] 后传入 ——
 * 「跟随系统」需要读 Compose 环境，不能放进必须在 JVM 上单测的领域模型（NFR-3.1）。
 *
 * [skinId] 在这里被解析成 [TileSkin] 并通过 [LocalTileSkin] 下发（V1.2 的 J-2）。
 * 未注册的编号由 [TileSkins.byId] 回退到默认皮肤，因此调用方不必自行校验。
 */
@Composable
fun LianLianKanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    skinId: Int = TileSkins.DEFAULT_ID,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTileSkin provides TileSkins.byId(skinId)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * 界面背景（SRS FR-13.8：背景采用渐变或纯色风格）。
 *
 * 用「底色 → 表面色」的极浅竖向渐变，比纯色多一点层次，又不会抢牌面的视觉重心。
 * 用当前配色方案推导而非写死颜色，因此浅色与深色下都成立。
 */
@Composable
fun appBackgroundBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        colors = listOf(
            colors.background,
            colors.surfaceVariant.copy(alpha = 0.55f),
        ),
    )
}
