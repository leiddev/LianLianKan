package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色对比度单测（SRS NFR-5.3：深色模式下界面元素对比度可辨）。
 *
 * 把主观的「看得清」变成可回归的数值：按 WCAG 2.1 的相对亮度公式计算对比度，
 * 要求成对的 `xxx` / `onXxx` 颜色达到 4.5:1（WCAG AA 正文标准）。
 *
 * 这条断言的价值在于**防止后续调色时无意踩坑** —— 手工换一个好看的色值很容易
 * 把某个深色配对的对比度压到阈值以下，而这种问题在真机上未必一眼看得出来。
 */
class ColorContrastTest {

    private companion object {
        /** WCAG AA 对正文的最低要求。 */
        const val MIN_CONTRAST = 4.5

        /** WCAG 对大字/图形元素放宽后的要求。 */
        const val MIN_CONTRAST_LARGE = 3.0
    }

    /**
     * WCAG 2.1 相对亮度。
     *
     * 注意这里用的是线性化后的分量（sRGB 反伽马），不是简单的平均值 ——
     * 直接用平均值会高估暗色的亮度，得出偏乐观的对比度。
     */
    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 逐对校验配色方案中承载文字的颜色组合。 */
    private fun assertPairs(
        schemeName: String,
        colors: ColorScheme,
        pairs: List<Triple<String, Color, Color>>,
        minimum: Double,
    ) {
        for ((label, foreground, background) in pairs) {
            val ratio = contrastRatio(foreground, background)
            assertTrue(
                "$schemeName 的「$label」对比度为 ${"%.2f".format(ratio)}，低于 $minimum:1（NFR-5.3）",
                ratio >= minimum,
            )
        }
    }

    private val textPairs = { colors: ColorScheme ->
        listOf(
            Triple("正文 onSurface / surface", colors.onSurface, colors.surface),
            Triple("正文 onBackground / background", colors.onBackground, colors.background),
            Triple("次要文字 onSurfaceVariant / surfaceVariant", colors.onSurfaceVariant, colors.surfaceVariant),
            Triple("主色按钮 onPrimary / primary", colors.onPrimary, colors.primary),
            Triple("主色容器 onPrimaryContainer / primaryContainer", colors.onPrimaryContainer, colors.primaryContainer),
            Triple("强调容器 onTertiaryContainer / tertiaryContainer", colors.onTertiaryContainer, colors.tertiaryContainer),
            Triple("错误容器 onErrorContainer / errorContainer", colors.onErrorContainer, colors.errorContainer),
        )
    }

    @Test
    fun `浅色主题的文字与背景对比度达标`() {
        assertPairs("浅色主题", LightColors, textPairs(LightColors), MIN_CONTRAST)
    }

    @Test
    fun `深色主题的文字与背景对比度达标`() {
        assertPairs("深色主题", DarkColors, textPairs(DarkColors), MIN_CONTRAST)
    }

    @Test
    fun `描边等图形元素在深浅两套主题下可辨`() {
        // 牌面未选中时用 surfaceVariant 底 + onSurfaceVariant 图案，选中时用 primary 描边，
        // 因此描边色只要求达到「非文本」的 3:1
        val pairs = { colors: ColorScheme ->
            listOf(
                Triple("选中描边 primary / surfaceVariant", colors.primary, colors.surfaceVariant),
                Triple("提示描边 tertiary / surfaceVariant", colors.tertiary, colors.surfaceVariant),
                Triple("轮廓 outline / surface", colors.outline, colors.surface),
            )
        }
        assertPairs("浅色主题", LightColors, pairs(LightColors), MIN_CONTRAST_LARGE)
        assertPairs("深色主题", DarkColors, pairs(DarkColors), MIN_CONTRAST_LARGE)
    }

    @Test
    fun `深浅两套主题确实不同 - 三态切换有实际效果`() {
        assertTrue(
            "浅色与深色的背景色不应相同",
            LightColors.background != DarkColors.background,
        )
        assertTrue(
            "深色主题的背景应比浅色主题暗",
            relativeLuminance(DarkColors.background) < relativeLuminance(LightColors.background),
        )
        assertTrue(
            "深色主题的正文色应比浅色主题亮",
            relativeLuminance(DarkColors.onSurface) > relativeLuminance(LightColors.onSurface),
        )
    }
}
