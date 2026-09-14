package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import me.tatarka.google.material.dynamiccolor.MaterialDynamicColors
import me.tatarka.google.material.hct.Hct
import me.tatarka.google.material.scheme.SchemeContent

/**
 * 一套主题配色（V1.3）。
 *
 * ### 生成方式
 * 每个主题只提供一个**种子色**，整套配色由**标准 M3 色调板算法**生成
 * （`material-color-utilities` 的 `SchemeContent`）—— 因此界面上的颜色是种子色
 * **按色调板取 tone 后**的结果，**不是种子色本身**。这是 V1.3 计划中 Q3-b 的确认结论。
 *
 * 选 `SchemeContent` 而非 M3 默认的 `SchemeTonalSpot`：前者保留种子色的**色相与彩度**，
 * 后者会把彩度固定为 36，与给定色值偏离更大。两者都按 tone 取值（浅色下 primary ≈ tone 40）。
 *
 * ### 只有浅色一套
 * V1.3 确认取消深色模式（计划 §9 的 Q2-c），因此每套配色只有 [light] 一套方案，
 * 不再有 `dark`。相应地从 SRS 中删除了 NFR-5.3 与 FR-13.2 —— 这是产品定位取舍，
 * 不是实现受阻，已在 SRS 修订历史中记录。
 */
data class ThemePalette(
    val id: Int,
    val seed: Color,
    val light: ColorScheme,
)

/**
 * 全部主题配色。
 *
 * 新增一套只需往 [all] 追加一项并加一条字符串资源（SRS NFR-4.2）；
 * `GameSession` 等逻辑层完全不感知配色。
 */
object ThemePalettes {

    const val DEFAULT_ID = 0

    /** 4 个种子色，由开发者指定。 */
    private val SEEDS: List<Color> = listOf(
        Color(0xFF8FD3F6), // 浅蓝
        Color(0xFFABCA14), // 黄绿
        Color(0xFFFE2472), // 玫红
        Color(0xFFFFBA07), // 琥珀
    )

    val all: List<ThemePalette> = SEEDS.mapIndexed { index, seed ->
        ThemePalette(id = index, seed = seed, light = schemeFrom(seed))
    }

    val default: ThemePalette get() = all[DEFAULT_ID]

    /** 按编号取配色；**未注册的编号回退到默认配色**，不抛异常（与 `TileSkins.byId` 同构）。 */
    fun byId(id: Int): ThemePalette = all.firstOrNull { it.id == id } ?: default

    /**
     * 由种子色生成一套浅色 M3 配色。
     *
     * `contrastLevel = 0.0` 即 M3 的标准对比度档位。M3 自身对每个角色都有对比度约束，
     * 生成结果仍会被 `ThemePaletteContrastTest` 按 WCAG 逐对复核 —— 双保险。
     */
    private fun schemeFrom(seed: Color): ColorScheme {
        val scheme = SchemeContent(Hct.fromInt(seed.toArgb()), false, 0.0)
        val colors = MaterialDynamicColors()

        fun role(pick: MaterialDynamicColors.() -> me.tatarka.google.material.dynamiccolor.DynamicColor): Color =
            Color(scheme.getArgb(colors.pick()))

        return lightColorScheme(
            primary = role { primary() },
            onPrimary = role { onPrimary() },
            primaryContainer = role { primaryContainer() },
            onPrimaryContainer = role { onPrimaryContainer() },
            inversePrimary = role { inversePrimary() },

            secondary = role { secondary() },
            onSecondary = role { onSecondary() },
            secondaryContainer = role { secondaryContainer() },
            onSecondaryContainer = role { onSecondaryContainer() },

            tertiary = role { tertiary() },
            onTertiary = role { onTertiary() },
            tertiaryContainer = role { tertiaryContainer() },
            onTertiaryContainer = role { onTertiaryContainer() },

            error = role { error() },
            onError = role { onError() },
            errorContainer = role { errorContainer() },
            onErrorContainer = role { onErrorContainer() },

            background = role { background() },
            onBackground = role { onBackground() },
            surface = role { surface() },
            onSurface = role { onSurface() },

            surfaceVariant = role { surfaceVariant() },
            onSurfaceVariant = role { onSurfaceVariant() },
            outline = role { outline() },
            outlineVariant = role { outlineVariant() },

            inverseSurface = role { inverseSurface() },
            inverseOnSurface = role { inverseOnSurface() },
        )
    }
}
