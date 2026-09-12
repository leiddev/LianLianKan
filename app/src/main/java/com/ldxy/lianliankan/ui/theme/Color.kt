package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 项目自有配色（SRS FR-13.1 / FR-13.2）。
 *
 * 是一套「青绿主色 + 琥珀强调色」的 Material 3 方案：主色用于选中高亮，
 * 强调色（tertiary）用于提示高亮与连线渐变，二者在明度上拉开，避免同时出现时混淆。
 *
 * ### 可访问性
 * SRS NFR-5.3 要求「深色模式下界面元素对比度可辨」。这里的原则是**只用成对的
 * `xxx` / `onXxx` 颜色**（Material 3 的设计约定就保证了配对内的对比度），
 * 并由 `ColorContrastTest` 按 WCAG 相对亮度公式逐对断言对比度达标 —— 把主观的
 * 「看得清」变成可回归的数值。
 */
internal object Palette {

    // ---- 浅色 ----
    val PrimaryLight = Color(0xFF00696E)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFF9CF1F6)
    val OnPrimaryContainerLight = Color(0xFF002022)

    val SecondaryLight = Color(0xFF4A6365)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFCCE8E9)
    val OnSecondaryContainerLight = Color(0xFF051F21)

    val TertiaryLight = Color(0xFF7B4E00)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFFFDDB0)
    val OnTertiaryContainerLight = Color(0xFF281800)

    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)

    val BackgroundLight = Color(0xFFF5FBFB)
    val OnBackgroundLight = Color(0xFF171D1D)
    val SurfaceVariantLight = Color(0xFFDAE4E5)
    val OnSurfaceVariantLight = Color(0xFF3F4949)
    val OutlineLight = Color(0xFF6F7979)

    // ---- 深色 ----
    val PrimaryDark = Color(0xFF4DD9E0)
    val OnPrimaryDark = Color(0xFF003739)
    val PrimaryContainerDark = Color(0xFF004F52)
    val OnPrimaryContainerDark = Color(0xFF9CF1F6)

    val SecondaryDark = Color(0xFFB0CCCD)
    val OnSecondaryDark = Color(0xFF1B3536)
    val SecondaryContainerDark = Color(0xFF324B4C)
    val OnSecondaryContainerDark = Color(0xFFCCE8E9)

    val TertiaryDark = Color(0xFFF0BD72)
    val OnTertiaryDark = Color(0xFF422C00)
    val TertiaryContainerDark = Color(0xFF5F4100)
    val OnTertiaryContainerDark = Color(0xFFFFDDB0)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val BackgroundDark = Color(0xFF0E1414)
    val OnBackgroundDark = Color(0xFFDEE4E4)
    val SurfaceVariantDark = Color(0xFF3F4949)
    val OnSurfaceVariantDark = Color(0xFFBEC8C9)
    val OutlineDark = Color(0xFF899393)
}

internal val LightColors = lightColorScheme(
    primary = Palette.PrimaryLight,
    onPrimary = Palette.OnPrimaryLight,
    primaryContainer = Palette.PrimaryContainerLight,
    onPrimaryContainer = Palette.OnPrimaryContainerLight,
    secondary = Palette.SecondaryLight,
    onSecondary = Palette.OnSecondaryLight,
    secondaryContainer = Palette.SecondaryContainerLight,
    onSecondaryContainer = Palette.OnSecondaryContainerLight,
    tertiary = Palette.TertiaryLight,
    onTertiary = Palette.OnTertiaryLight,
    tertiaryContainer = Palette.TertiaryContainerLight,
    onTertiaryContainer = Palette.OnTertiaryContainerLight,
    error = Palette.ErrorLight,
    onError = Palette.OnErrorLight,
    errorContainer = Palette.ErrorContainerLight,
    onErrorContainer = Palette.OnErrorContainerLight,
    background = Palette.BackgroundLight,
    onBackground = Palette.OnBackgroundLight,
    surface = Palette.BackgroundLight,
    onSurface = Palette.OnBackgroundLight,
    surfaceVariant = Palette.SurfaceVariantLight,
    onSurfaceVariant = Palette.OnSurfaceVariantLight,
    outline = Palette.OutlineLight,
)

internal val DarkColors = darkColorScheme(
    primary = Palette.PrimaryDark,
    onPrimary = Palette.OnPrimaryDark,
    primaryContainer = Palette.PrimaryContainerDark,
    onPrimaryContainer = Palette.OnPrimaryContainerDark,
    secondary = Palette.SecondaryDark,
    onSecondary = Palette.OnSecondaryDark,
    secondaryContainer = Palette.SecondaryContainerDark,
    onSecondaryContainer = Palette.OnSecondaryContainerDark,
    tertiary = Palette.TertiaryDark,
    onTertiary = Palette.OnTertiaryDark,
    tertiaryContainer = Palette.TertiaryContainerDark,
    onTertiaryContainer = Palette.OnTertiaryContainerDark,
    error = Palette.ErrorDark,
    onError = Palette.OnErrorDark,
    errorContainer = Palette.ErrorContainerDark,
    onErrorContainer = Palette.OnErrorContainerDark,
    background = Palette.BackgroundDark,
    onBackground = Palette.OnBackgroundDark,
    surface = Palette.BackgroundDark,
    onSurface = Palette.OnBackgroundDark,
    surfaceVariant = Palette.SurfaceVariantDark,
    onSurfaceVariant = Palette.OnSurfaceVariantDark,
    outline = Palette.OutlineDark,
)
