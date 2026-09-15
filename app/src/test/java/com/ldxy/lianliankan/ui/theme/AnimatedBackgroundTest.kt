package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 动态背景的约束不变量测试（V1.5；修订 2 重写）。
 *
 * ### 这份测试改过两次，每次都是被真机打回来的
 * 1. **修订 0 的 6 条断言全部通过，而真机「完全看不出背景在动」。** 原因是断言测的是**配方**
 *    （透明度 ≤ 0.08、周期 ≥ 20 s、用色是中性角色），而「看得见」取决于**成品** ——
 *    合成后的像素差 = 透明度 ×（底色 − 光斑色）。当时的光斑色与底色的差只有 29 / 57 级，
 *    乘上 0.033~0.055 的透明度后只有 **1~2 级（约 0.5%）**，5 个光斑里 4 个在数学上不可见。
 *    → 于是有了 `每个光斑在其呼吸峰值处都看得见` 这条**成品断言**。
 * 2. **修订 1 的 8 条断言全部通过，而真机「每个界面都没看到动态背景」。** 这次与颜色无关：
 *    动画根本没有推进。Compose 会按系统的「动画程序时长缩放」折算动画时长，缩放为 0 时
 *    进度被直接取到动画结尾（恒为 1.0），光斑全部停在屏幕上方之外。
 *    → 于是有了 `进度按上浮周期循环` 这条**时钟断言**，以及把时钟改成自己数帧的实现。
 *
 * 两次的共同教训：**测试要盯住「外部可观察的结果」，而不是「我写下的那几个数字」。**
 * 因此下面既断言像素差（成品），也断言进度函数本身的行为（时钟），
 * 而这些量都由纯函数给出、与 Composable 和系统设置无关。
 *
 * ### 为什么这里的上下限是字面量，而不用生产代码里的常量
 * 若断言写成 `spec.maxAlpha <= MAX_ALPHA`，那么把 `MAX_ALPHA` 改成 0.5 也会通过 ——
 * 测的是「两处抄写一致」，不是「符合需求」。所以约束值在这里独立写一遍（并注明出处），
 * 生产代码里的常量只用于给读者指路。
 *
 * ### 为什么没有「尊重系统减少动效」的测试
 * 计划 §13 的 Q3 已确认 **不读**系统偏好（取值 B），§6 整节作废、验收项 A-3 作废。
 * 功能不存在，就不该有测试 —— 留下一个永远通过的测试反而会让人以为该功能已实现。
 * 这处取舍记在 README 的「已知取舍」表里。
 */
class AnimatedBackgroundTest {

    private companion object {
        /** 计划 §3：元素数量 ≤ 8 个（控制绘制预算与视觉噪声）。 */
        const val MAX_ORB_COUNT = 8

        /** 「背景氛围」至少要有几个光斑 —— 少于 3 个不成其为背景，而是画面上多了两个圆。 */
        const val MIN_ORB_COUNT = 3

        /** 计划 §3：单个光斑的透明度上限。见 `DefaultBackgroundSpec` 对 0.055 → 0.13 的复盘。 */
        const val MAX_ALPHA = 0.15

        /** 计划 §3：上浮周期下限（毫秒），避免形成被眼睛抓住的「节律」。 */
        const val MIN_RISE_MILLIS = 15_000

        /**
         * 可见性区间（**成品指标**，单位：0~255 的单通道差）。
         *
         * 下界 14（约 5.5%）：大面积柔和的色块差异，低于这个量级就不该指望「看得见」——
         * 修订 0 实测只有 1~2 级。上界 34（约 13%）：再高就会从「氛围」变成「画面上有东西」，
         * 并会明显削弱棋盘上牌与背景的边界（Q2 = B 没有底板）。
         */
        const val MIN_VISIBLE_DELTA = 14.0
        const val MAX_VISIBLE_DELTA = 34.0
    }

    /** 承载游戏语义的强调色角色，背景一律不得取用（计划 §3 最后一条 / 风险 R-1）。 */
    private fun semanticAccentRoles(colors: ColorScheme): Map<String, Color> = mapOf(
        "primary（选中）" to colors.primary,
        "onPrimary" to colors.onPrimary,
        "primaryContainer（选中牌的底色）" to colors.primaryContainer,
        "onPrimaryContainer" to colors.onPrimaryContainer,
        "tertiary（提示）" to colors.tertiary,
        "onTertiary" to colors.onTertiary,
        "tertiaryContainer（提示牌的底色）" to colors.tertiaryContainer,
        "onTertiaryContainer" to colors.onTertiaryContainer,
        "error（错误）" to colors.error,
        "onError" to colors.onError,
        "errorContainer（错误牌的底色）" to colors.errorContainer,
        "onErrorContainer" to colors.onErrorContainer,
    )

    /** 两个颜色之间最大的单通道差，0~255。 */
    private fun channelDelta(a: Color, b: Color): Double = maxOf(
        abs(a.red - b.red),
        abs(a.green - b.green),
        abs(a.blue - b.blue),
    ) * 255.0

    /**
     * 基底竖向渐变的两个端点色（见 `appBackgroundBrush`）。
     *
     * 光斑会被叠在这条渐变的任意位置上，因此可见性必须**在两端都成立**：
     * 上端是 `background`，下端是 `surfaceVariant`。只测 `background`（较亮、像素差更大）
     * 会高估可见性。
     */
    private fun baseColorExtremes(colors: ColorScheme): Map<String, Color> = mapOf(
        "渐变的亮端（background）" to colors.background,
        "渐变的暗端（surfaceVariant）" to colors.surfaceVariant,
    )

    // ==================================================== 时钟（修订 2 新增，对应第二次真机返工）

    @Test
    fun `进度按上浮周期循环 - 且初始相位互相错开`() {
        // 修订 1 的真机现象是「什么都没有」，根源是进度恒为 1.0（动画时长被系统缩放为 0），
        // 而四个光斑的初始相位又都是 0 —— 于是全部停在屏幕上方之外，屏幕上真的一个都不剩。
        // 这条断言把「时钟」这件事钉在纯函数上：不依赖 Composable，也不依赖系统设置。
        val period = DefaultBackgroundSpec.riseMillis

        // ① 每个光斑的初始相位必须互不相同：这样 t = 0 时至少有几个光斑已经落在屏幕内，
        //    万一时间推进再次失效，现象是「看得见但不动」而不是「什么都没有」。
        val offsets = (0 until DefaultBackgroundSpec.orbCount)
            .map { backgroundOrbPhase(0L, period, it) }
        assertEquals("初始相位应两两不同", offsets.size, offsets.toSet().size)
        assertTrue(
            "初始相位应至少有一个落在屏幕中部（0.2~0.8），否则启动后头几秒屏幕上没有光斑：$offsets",
            offsets.any { it in 0.2..0.8 },
        )

        // ② 进度始终落在 [0, 1)，并且确实随时间推进（不是常量）。
        for (index in 0 until DefaultBackgroundSpec.orbCount) {
            var previous = backgroundOrbPhase(0L, period, index)
            var moved = false
            for (step in 1..200) {
                val elapsed = period.toLong() * step / 100
                val phase = backgroundOrbPhase(elapsed, period, index)
                assertTrue("第 $index 个光斑的进度 $phase 应落在 [0, 1)", phase >= 0.0 && phase < 1.0)
                if (abs(phase - previous) > 1e-9) moved = true
                previous = phase
            }
            assertTrue("第 $index 个光斑的进度不随时间变化", moved)
        }

        // ③ 恰好走过一个周期后回到原处（循环无缝）。
        for (index in 0 until DefaultBackgroundSpec.orbCount) {
            val at0 = backgroundOrbPhase(0L, period, index)
            val atPeriod = backgroundOrbPhase(period.toLong(), period, index)
            assertTrue(
                "第 $index 个光斑走过一个周期后应回到原处：$at0 vs $atPeriod",
                abs(at0 - atPeriod) < 1e-6,
            )
        }

        // ④ 非法周期不崩（退化输入返回初始相位）。
        for (index in 0 until DefaultBackgroundSpec.orbCount) {
            for (bad in listOf(0, -1, Int.MIN_VALUE)) {
                val phase = backgroundOrbPhase(12345L, bad, index)
                assertTrue("周期为 $bad 时进度 $phase 应仍落在 [0, 1)", phase >= 0.0 && phase < 1.0)
            }
        }
        // 越界序号同样不崩（倍率表按取模取用）。
        for (index in listOf(-3, -1, 99)) {
            val phase = backgroundOrbPhase(1000L, period, index)
            assertTrue("序号 $index 时应仍返回合法进度，实际 $phase", phase >= 0.0 && phase < 1.0)
        }
    }

    // ==================================================== 计划 §3 的硬约束

    @Test
    fun `光斑数量在 3 到 8 个之间`() {
        // 计划 §3 / 风险 R-1（视觉噪声）与 R-4（与棋盘动画抢帧的绘制预算）。
        val count = DefaultBackgroundSpec.orbCount
        assertTrue("光斑数量 $count 少于 $MIN_ORB_COUNT 个，不成其为背景氛围", count >= MIN_ORB_COUNT)
        assertTrue("光斑数量 $count 超过计划 §3 的上限 $MAX_ORB_COUNT 个", count <= MAX_ORB_COUNT)
    }

    @Test
    fun `每个光斑的峰值透明度都不超过上限`() {
        // 计划 §3 / 风险 R-1。注意这只是**配方**约束，真正管用的是下面那条成品断言。
        val spec = DefaultBackgroundSpec
        assertTrue("峰值透明度应为正数，否则背景不可见", spec.maxAlpha > 0.0)
        assertTrue("峰值透明度 ${spec.maxAlpha} 超过上限 $MAX_ALPHA", spec.maxAlpha <= MAX_ALPHA)

        // 运行时用的是 backgroundOrbPeakAlpha 的倍率表，必须确认**实际画出来的每一个光斑**
        // 都不超过上限，且上限确实被用到（不是虚设）。含越界序号，函数应取模而不是抛异常。
        var actualMax = 0.0
        for (index in -2..spec.orbCount + 5) {
            val alpha = backgroundOrbPeakAlpha(spec, index)
            assertTrue("第 $index 个光斑的峰值透明度 $alpha 应为正数", alpha > 0.0)
            assertTrue(
                "第 $index 个光斑的峰值透明度 $alpha 超过上限 $MAX_ALPHA",
                alpha <= MAX_ALPHA,
            )
            actualMax = maxOf(actualMax, alpha)
        }
        assertTrue(
            "倍率表应至少有一档取到 maxAlpha，否则 maxAlpha 只是个装饰性数字",
            abs(actualMax - spec.maxAlpha) < 1e-9,
        )
    }

    @Test
    fun `每个光斑在其呼吸峰值处都看得见`() {
        // ★ 本文件最核心的一条：断言**成品**而不是配方。
        //
        // 「看得见」= 光斑中心处合成后的颜色相对底色有明显的像素差。这条断言在修订 0 的
        // 实现上会立刻失败（4 个光斑只有 1~2 级差），这正是它存在的意义。
        //
        // 逐主题色 × 逐光斑 × 渐变两端，四套配色都要成立（颜色由 ColorScheme 推导，
        // 不是写死的一组灰）。
        val spec = DefaultBackgroundSpec
        for (palette in ThemePalettes.all) {
            val colors = palette.light
            val orbColors = backgroundOrbColors(colors)
            assertTrue("主题 ${palette.id} 的背景用色不应为空", orbColors.isNotEmpty())

            for ((baseLabel, base) in baseColorExtremes(colors)) {
                for (index in 0 until spec.orbCount) {
                    val orb = orbColors[index % orbColors.size]
                    val alpha = backgroundOrbPeakAlpha(spec, index)
                    val composite = backgroundOrbComposite(base, orb, alpha)
                    val delta = channelDelta(base, composite)

                    assertTrue(
                        "主题 ${palette.id} / $baseLabel / 第 $index 个光斑：" +
                            "峰值处的像素差只有 ${"%.2f".format(delta)} 级（透明度 $alpha，" +
                            "光斑色 $orb），低于可见下界 $MIN_VISIBLE_DELTA 级 —— " +
                            "光斑在数学上就看不见，真机上也一定看不见",
                        delta >= MIN_VISIBLE_DELTA,
                    )
                    assertTrue(
                        "主题 ${palette.id} / $baseLabel / 第 $index 个光斑：" +
                            "峰值处的像素差 ${"%.2f".format(delta)} 级超过上界 $MAX_VISIBLE_DELTA 级 —— " +
                            "背景会从「氛围」变成「画面上有东西」，并削弱棋盘上牌与背景的边界",
                        delta <= MAX_VISIBLE_DELTA,
                    )
                }
            }
        }
    }

    @Test
    fun `呼吸不会让光斑完全消失 - 也不会超过峰值`() {
        // 参照实现（FloatingOrbs）的呼吸会降到 0：在深色底上像闪烁的光点，在浅色底上会变成
        // 「一块雾忽然出现又消失」。本实现刻意留了底（见 BREATH_FLOOR）。
        var min = Double.MAX_VALUE
        var max = 0.0
        for (step in 0 until 1000) {
            val breath = backgroundOrbBreath(step / 1000.0)
            min = minOf(min, breath)
            max = maxOf(max, breath)
        }
        assertTrue("呼吸系数最小值 $min 应大于 0（不做完全消失的闪烁）", min > 0.15)
        assertTrue("呼吸系数最大值 $max 不应超过 1（否则会突破透明度上限）", max <= 1.0 + 1e-9)
        assertTrue("呼吸幅度只有 ${max - min}，看不出明暗变化", max - min >= 0.3)
        assertTrue("呼吸几乎一直在最亮处，等于没有呼吸", min <= 0.7)
    }

    @Test
    fun `上浮周期不短于 15 秒 - 且四路互不相同`() {
        // 计划 §3 / 风险 R-1：周期太短会形成「节律」，吸引注意力。
        val spec = DefaultBackgroundSpec
        assertTrue(
            "基准周期 ${spec.riseMillis}ms 短于下限 ${MIN_RISE_MILLIS}ms",
            spec.riseMillis >= MIN_RISE_MILLIS,
        )

        // 多路不同速度是「不要同步」的实现手段，但不能成为偷偷提速的借口：
        // 逐路复核，避免日后有人为了「更有生气」把某一档的倍率改到 1 以下。
        val periods = backgroundRisePeriods(spec)
        assertTrue("应有多路不同速度的上浮，否则光斑会整齐划一地移动", periods.size >= 2)
        for ((index, period) in periods.withIndex()) {
            assertTrue(
                "第 $index 路上浮的周期 ${period}ms 短于下限 ${MIN_RISE_MILLIS}ms",
                period >= MIN_RISE_MILLIS,
            )
        }
        assertTrue(
            "各路周期应互不相同（本断言同时挡住「倍率被改成同一个值」）",
            periods.toSet().size == periods.size,
        )
    }

    @Test
    fun `光斑半径是相对短边的大尺寸比例`() {
        // 形态参照 FloatingOrbs（0.24~0.36 倍屏宽）+ 风险 R-2（太小会像具象形状）。
        val range = DefaultBackgroundSpec.radiusRange
        assertTrue(
            "半径下界 ${range.start} 偏小：光斑会退化成可数的圆点，下界应不低于 0.15",
            range.start >= 0.15,
        )
        assertTrue(
            "半径上界 ${range.endInclusive} 应不超过屏幕短边的 1 倍",
            range.endInclusive <= 1.0,
        )

        // 运行时取值也必须落在区间内（backgroundOrbRadius 按序号均匀铺开）。
        for (index in 0 until DefaultBackgroundSpec.orbCount) {
            val radius = backgroundOrbRadius(DefaultBackgroundSpec, index)
            assertTrue("第 $index 个光斑的半径比例 $radius 不在区间 $range 内", radius in range)
        }
    }

    @Test
    fun `半径区间上下界合法 - 且取值始终落在区间内`() {
        // 这条测的是 backgroundOrbRadius 本身的不变量：区间合法（lower <= upper、都落在 (0,1]）
        // 且任何输入下取值都不越界、也不抛异常（退化输入不该崩）。
        val cases = listOf(
            "默认" to DefaultBackgroundSpec,
            "上下界相等" to DefaultBackgroundSpec.copy(radiusRange = 0.5..0.5),
            "满区间" to DefaultBackgroundSpec.copy(radiusRange = 0.1..1.0),
            "单个光斑" to DefaultBackgroundSpec.copy(orbCount = 1),
            "空光斑（退化输入）" to DefaultBackgroundSpec.copy(orbCount = 0),
        )
        for ((label, spec) in cases) {
            val range = spec.radiusRange
            assertTrue("$label：半径区间下界应大于 0", range.start > 0.0)
            assertTrue("$label：半径区间上界应不超过 1", range.endInclusive <= 1.0)
            assertTrue("$label：半径区间下界不应大于上界", range.start <= range.endInclusive)

            // 含越界序号：函数应夹取而不是抛异常。
            for (index in -2..spec.orbCount + 1) {
                val radius = backgroundOrbRadius(spec, index)
                assertTrue("$label：序号 $index 的半径比例 $radius 越出区间 $range", radius in range)
            }
        }
    }

    @Test
    fun `背景用色不含承载游戏语义的强调色`() {
        // 计划 §3 最后一条 / 风险 R-1。
        // primary 承载「选中」、tertiary 承载「提示」、error 承载「错误」，而三个 *Container
        // 角色正是牌的底色（见 TileFaceBox）—— 背景若从这些角色取色，会直接削弱棋盘上的
        // 状态信号与牌的辨识度。
        for (palette in ThemePalettes.all) {
            val colors = palette.light
            val orbColors = backgroundOrbColors(colors)
            assertTrue("主题 ${palette.id} 的背景用色不应为空", orbColors.isNotEmpty())

            val accents = semanticAccentRoles(colors)
            for ((role, accent) in accents) {
                for (orbColor in orbColors) {
                    assertTrue(
                        "主题 ${palette.id} 的背景用色 $orbColor 与强调色 $role 取到了同一个值：" +
                            "背景不得使用承载游戏语义的颜色",
                        orbColor != accent,
                    )
                }
            }
        }
    }
}
