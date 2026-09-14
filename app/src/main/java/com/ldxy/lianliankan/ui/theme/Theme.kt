package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush

/**
 * 应用主题入口（SRS FR-13.1：采用 Material 3 设计规范）。
 *
 * V1.3 起主题是「4 种主题色」而不是旧的「深浅三态」：深色模式已整体取消，
 * 每套配色只有一份浅色方案（见 `Palette.kt`），因此本函数**不再接收 `darkTheme`** ——
 * 传「是否深色」这件事在新方案下没有意义，留着参数只会诱导调用方再把它接回来。
 *
 * [paletteId] 与 [skinId] 都按编号传入、由 [ThemePalettes.byId] / [TileSkins.byId]
 * 回退到默认值，所以调用方不必自行校验。圆角与间距见 `Shape.kt`，皮肤见 `Skin.kt`。
 *
 * [skinId] 在这里被解析成 [TileSkin] 并通过 [LocalTileSkin] 下发（V1.2 的 J-2）。
 */
@Composable
fun LianLianKanTheme(
    paletteId: Int = ThemePalettes.DEFAULT_ID,
    skinId: Int = TileSkins.DEFAULT_ID,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTileSkin provides TileSkins.byId(skinId)) {
        MaterialTheme(
            colorScheme = ThemePalettes.byId(paletteId).light,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * 界面背景（SRS FR-13.8：背景采用渐变或纯色风格）。
 *
 * 用「底色 → 表面色」的极浅竖向渐变，比纯色多一点层次，又不会抢牌面的视觉重心。
 * 用当前配色方案推导而非写死颜色，因此 4 套主题色都成立（V1.3 起也只有浅色一套）。
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
