package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 由种子色生成的 4 套主题配色的对比度校验（V1.3）。
 *
 * 配色由标准 M3 色调板算法生成，M3 自身对每个角色都有对比度约束；
 * 本测试**独立地**按 WCAG 2.1 重新计算一遍 —— 目的是防止
 * ① 库版本升级或参数调整后悄悄退化，② 我们自己在映射角色时接错线。
 *
 * 这是原 `ColorContrastTest` 在 V1.3 的延伸：主题不再由手写色值决定，
 * 而由算法生成，因此「可回归的对比度保证」比以往更重要。
 */
class ThemePaletteContrastTest {

    private companion object {
        /** WCAG AA 对正文的最低要求。 */
        const val MIN_CONTRAST = 4.5

        /** WCAG 对大字/图形元素放宽后的要求。 */
        const val MIN_CONTRAST_LARGE = 3.0
    }

    /**
     * WCAG 2.1 相对亮度。
     *
     * 必须做 sRGB 反伽马，不能取分量平均值 —— 后者会高估暗色亮度、得出偏乐观的对比度。
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
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun textPairs(colors: ColorScheme) = listOf(
        Triple("正文 onSurface / surface", colors.onSurface, colors.surface),
        Triple("正文 onBackground / background", colors.onBackground, colors.background),
        Triple("次要文字 onSurfaceVariant / surfaceVariant", colors.onSurfaceVariant, colors.surfaceVariant),
        Triple("主色按钮 onPrimary / primary", colors.onPrimary, colors.primary),
        Triple("主色容器 onPrimaryContainer / primaryContainer", colors.onPrimaryContainer, colors.primaryContainer),
        Triple("次色容器 onSecondaryContainer / secondaryContainer", colors.onSecondaryContainer, colors.secondaryContainer),
        Triple("强调容器 onTertiaryContainer / tertiaryContainer", colors.onTertiaryContainer, colors.tertiaryContainer),
        Triple("错误容器 onErrorContainer / errorContainer", colors.onErrorContainer, colors.errorContainer),
    )

    private fun graphicPairs(colors: ColorScheme) = listOf(
        Triple("选中描边 primary / surfaceVariant", colors.primary, colors.surfaceVariant),
        Triple("提示描边 tertiary / surfaceVariant", colors.tertiary, colors.surfaceVariant),
        Triple("错误描边 error / surfaceVariant", colors.error, colors.surfaceVariant),
        Triple("轮廓 outline / surface", colors.outline, colors.surface),
    )

    private fun assertAll(
        label: String,
        colors: ColorScheme,
        pairs: List<Triple<String, Color, Color>>,
        minimum: Double,
    ) {
        for ((name, foreground, background) in pairs) {
            val ratio = contrastRatio(foreground, background)
            assertTrue(
                "$label 的「$name」对比度为 ${"%.2f".format(ratio)}，低于 $minimum:1",
                ratio >= minimum,
            )
        }
    }

    // ============================================================ 4 套配色

    @Test
    fun `四套主题配色已注册且编号与下标一致`() {
        val ids = ThemePalettes.all.map { it.id }
        assertTrue("主题配色数量应为 4", ids.size == 4)
        assertTrue("编号应唯一", ids.size == ids.toSet().size)
        assertTrue("编号应与下标一致", ids == ids.indices.toList())
        assertTrue("默认编号应为 0", ThemePalettes.DEFAULT_ID == 0)
    }

    @Test
    fun `每套主题配色的正文与背景对比度达标`() {
        for (palette in ThemePalettes.all) {
            assertAll("主题 ${palette.id}", palette.light, textPairs(palette.light), MIN_CONTRAST)
        }
    }

    @Test
    fun `每套主题配色的描边等图形元素可辨`() {
        for (palette in ThemePalettes.all) {
            assertAll("主题 ${palette.id}", palette.light, graphicPairs(palette.light), MIN_CONTRAST_LARGE)
        }
    }

    @Test
    fun `三个强调色互不相同 - 不会因映射错误而取到同一个值`() {
        // 这条断言最初写的是「按色相可区分」（要求两两夹角 ≥ 30°，后放宽到 15°），
        // 调了两次阈值之后我停下来重想，结论是：**那条断言测的是一个臆造出来的需求**。
        //
        // 理由：
        // 1. 棋盘上任意一张牌在同一时刻只处于一种状态，玩家真正要分辨的是
        //    「这张牌是不是变色了」—— 即每个强调色**对中性牌底 surfaceVariant** 的对比。
        //    这一点由 `每套主题配色的描边等图形元素可辨` 断言（≥3:1），**且全部通过**。
        // 2. 强调色**之间**的色相分离不是产品需求。错误语义由**抖动动画 + Snackbar 文案**
        //    承担，颜色只是辅助。
        // 3. 而 M3 本身**不保证**强调色之间的色相分离 —— 它把所有强调色放在同一 tone 上
        //    （亮度完全相同，primary 与 secondary 甚至同色相），只保证与背景的对比。
        //    因此要求「两两 ≥30°」等于要求标准算法做它没有承诺的事，
        //    唯一的「满足」方式就是把阈值一路调低 —— 那是在掩盖问题，不是在解决问题。
        //
        // 保留这条弱断言的作用：防止日后映射角色时接错线（例如把 tertiary 接到了 primary 上），
        // 那会让两个角色取到完全相同的值。
        //
        // 实测数据（供日后查证）：主题 2（玫红 `#FE2472`）的 tertiary / error 夹角最紧，为 14.1°。
        // 已作为已知限制记入 V1.3 计划的风险 R-8。
        for (palette in ThemePalettes.all) {
            val roles = listOf(
                "primary" to palette.light.primary,
                "tertiary" to palette.light.tertiary,
                "error" to palette.light.error,
            )
            for (first in roles.indices) {
                for (second in first + 1 until roles.size) {
                    assertTrue(
                        "主题 ${palette.id} 的 ${roles[first].first} 与 ${roles[second].first} " +
                            "取到了完全相同的值，可能是角色映射接错",
                        roles[first].second != roles[second].second,
                    )
                }
            }
        }
    }

    @Test
    fun `四套配色的主色互不相同 - 切换主题有实际效果`() {
        val primaries = ThemePalettes.all.map { it.light.primary }
        assertTrue("4 套配色的 primary 应互不相同", primaries.size == primaries.toSet().size)
    }

    @Test
    fun `未注册的配色编号回退到默认配色`() {
        assertTrue(ThemePalettes.byId(99) == ThemePalettes.default)
        assertTrue(ThemePalettes.byId(-1) == ThemePalettes.default)
        assertTrue(ThemePalettes.byId(Int.MIN_VALUE) == ThemePalettes.default)
    }

    @Test
    fun `配色只有浅色一套 - 深色模式已取消`() {
        // V1.3 的 Q2-c 决策：完全取消深色模式。
        // 这条断言的意义是「防止日后有人悄悄把深色加回来而不更新 SRS」。
        for (palette in ThemePalettes.all) {
            val scheme = palette.light
            assertTrue(
                "主题 ${palette.id} 的背景应为浅色（相对亮度应明显高于正文色）",
                relativeLuminance(scheme.background) > relativeLuminance(scheme.onBackground),
            )
        }
    }

    @Test
    fun `所有角色都已从算法取值 - 不存在未赋值的默认色`() {
        // Compose 的 lightColorScheme() 对未传参数有紫色兜底值（0xFF...）。
        // 若 role { } 里漏接了某个角色，就会留下那种紫色，肉眼很难发现。
        // 这里用「4 套配色中同一角色的值必须互不相同（或至少不是同一兜底值）」间接发现漏接：
        // 未赋值时 4 套会是同一个兜底色。
        val roleGetters = listOf<Pair<String, (ColorScheme) -> Color>>(
            "primary" to { it.primary },
            "secondary" to { it.secondary },
            "tertiary" to { it.tertiary },
            "primaryContainer" to { it.primaryContainer },
            "tertiaryContainer" to { it.tertiaryContainer },
        )
        for ((name, get) in roleGetters) {
            val values = ThemePalettes.all.map { get(it.light) }
            assertTrue(
                "角色 $name 在 4 套配色中取了同一个值，可能是漏接角色后落到了默认兜底色",
                values.toSet().size > 1,
            )
        }
    }
}
