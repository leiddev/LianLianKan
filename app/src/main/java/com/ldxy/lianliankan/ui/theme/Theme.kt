package com.ldxy.lianliankan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 应用主题入口（SRS FR-13.1：采用 Material 3 设计规范）。
 *
 * 目前仅支持跟随系统深浅色；SRS FR-11.3 的三态（跟随系统 / 浅色 / 深色）
 * 将在 M8 接入设置持久化后由调用方传入 [darkTheme]。
 */
@Composable
fun LianLianKanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
