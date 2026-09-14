package com.ldxy.lianliankan.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.ldxy.lianliankan.R
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
    /** 设置页展示用与读屏用的名字。用资源 id 而非字符串，便于本地化。 */
    @StringRes val nameRes: Int,
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

    /** 4 个种子色，由开发者指定（色值见 SRS FR-11.3）。 */
    private val SEEDS: List<Pair<Int, Color>> = listOf(
        R.string.theme_color_blue to Color(0xFF8FD3F6), // 浅蓝
        R.string.theme_color_lime to Color(0xFFABCA14), // 黄绿
        R.string.theme_color_pink to Color(0xFFFE2472), // 玫红
        R.string.theme_color_amber to Color(0xFFFFBA07), // 琥珀
    )

    val all: List<ThemePalette> = SEEDS.mapIndexed { index, (nameRes, seed) ->
        ThemePalette(id = index, nameRes = nameRes, seed = seed, light = schemeFrom(seed))
    }

    val default: ThemePalette get() = all[DEFAULT_ID]

    /** 按编号取配色；**未注册的编号回退到默认配色**，不抛异常（与 `TileSkins.byId` 同构）。 */
    fun byId(id: Int): ThemePalette = all.firstOrNull { it.id == id } ?: default

    /**
     * 种子色与白色的混合比例。
     *
     * **为什么需要它**：直接把高彩度的种子色交给 `SchemeContent`，产出的 `primary`
     * 会明显饱和偏重（例如 `#FE2472` 的彩度高达 80）。先把种子与白色混合，
     * 相当于**降低彩度**，整套配色的观感更柔和。
     *
     * **它不改变明度**：M3 的 `primary` 固定取 tone 40，与种子明度无关，
     * 因此混合白色只降饱和、不会让主色变浅。要让主色真正变浅必须改 tone，
     * 但那会触碰文字与描边的对比度下限（见 [PRIMARY_TONE] 的说明）。
     */
    private const val SOFTEN_RATIO = 0.35f

    /**
     * `primary` 的 tone。M3 浅色方案默认为 40。
     *
     * 这里保持 40 而**不**调浅，原因是一个硬约束：`primary` 既要承载白字（`onPrimary`），
     * 又要作为**选中态描边**与牌底 `surfaceVariant`（tone 90）拉开 3:1。
     *
     * | tone | 配白字 | 配深色字 | 对 surfaceVariant |
     * |---|---|---|---|
     * | 40 | 4.8:1 ✅ | — | 满足 3:1 ✅ |
     * | 50 | 4.48:1 ❌ | 3.8:1 ❌ | 临界 |
     * | 70 | — | 9.8:1 ✅ | **1.67:1 ❌ 描边几乎不可见** |
     *
     * tone 50 是「两种文字色都不达标」的死区；再浅就必须跳到 tone 60+ 配深色字，
     * 而那会让选中边框失去对比。因此「让主色变浅」与「选中态清晰可辨」直接冲突，
     * 当前选择优先保证后者。
     */
    private const val PRIMARY_TONE = 40

    /**
     * 由种子色生成一套浅色 M3 配色。
     *
     * `contrastLevel = 0.0` 即 M3 的标准对比度档位。生成结果仍会被
     * `ThemePaletteContrastTest` 按 WCAG 逐对复核 —— 双保险。
     */
    private fun schemeFrom(seed: Color): ColorScheme {
        // 先降彩度（与白色混合），再交给标准 M3 算法
        val softened = lerp(seed, Color.White, SOFTEN_RATIO)
        val scheme = SchemeContent(Hct.fromInt(softened.toArgb()), false, 0.0)
        val colors = MaterialDynamicColors()

        fun role(pick: MaterialDynamicColors.() -> me.tatarka.google.material.dynamiccolor.DynamicColor): Color =
            Color(scheme.getArgb(colors.pick()))

        return lightColorScheme(
            primary = Color(scheme.primaryPalette.tone(PRIMARY_TONE)),
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
