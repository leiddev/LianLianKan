package com.ldxy.lianliankan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动态背景的约束不变量测试（V1.5）。
 *
 * ### 这些断言的意义
 * 计划 §3 把「周期 / 透明度 / 数量 / 形状 / 颜色」五条写成了**硬约束**，理由是动态背景
 * 在本项目里最大的风险不是性能，而是**干扰玩法**（风险 R-1，等级高）—— 玩家需要持续扫视棋盘
 * 找配对。这类参数最典型的事故方式就是「某次调参顺手把透明度从 0.055 提到 0.15，
 * 只在真机上『感觉还行』」，事后没有任何东西会红。因此本文件的作用是**把常量钉住**：
 *
 * - 每条断言都标注了它对应的计划条款与风险编号，改坏时会直接失败；
 * - 断言用的是 `DefaultBackgroundSpec` 与运行时真正调用的两个函数
 *   （[backgroundBlobRadius] / [backgroundBlobAlpha]），而不是另抄一份期望值 ——
 *   否则测试通过只说明「抄写一致」，说明不了「实际画出来的东西合规」。
 *
 * ### 为什么没有「尊重系统减少动效」的测试
 * 计划 §13 的 Q3 已确认 **不读**系统偏好（取值 B），§6 整节作废、验收项 A-3 作废。
 * 功能不存在，就不该有测试 —— 留下一个永远通过的测试反而会让人以为该功能已实现。
 * 这处取舍记在 README 的「已知取舍」表里。
 */
class AnimatedBackgroundTest {

    private companion object {
        /** 计划 §3：元素数量 ≤ 8 个（控制绘制预算，也控制视觉噪声）。 */
        const val MAX_BLOB_COUNT = 8

        /** 计划 §3：单个元素透明度 ≤ 0.08（必须弱到「看久了才注意到」）。 */
        const val MAX_ALPHA = 0.08

        /** 计划 §3：运动周期 ≥ 20 s（周期太短会形成「节律」，吸引注意力）。 */
        const val MIN_PERIOD_MILLIS = 20_000

        /**
         * 半径比例的期望下界（相对屏幕短边）。
         *
         * 计划 §13 Q1 选定的视觉构想是「**大尺寸**柔和光斑」：半径太小会退化成可数的圆点，
         * 反而更像牌的轮廓（风险 R-2）。这里把「大」钉成 0.3，防止日后被顺手改小。
         */
        const val MIN_RADIUS_RATIO = 0.3
    }

    /** 承载游戏语义的强调色角色，背景一律不得取用（计划 §3 最后一条 / 风险 R-1）。 */
    private fun semanticAccentRoles(colors: ColorScheme): Map<String, Color> = mapOf(
        "primary（选中）" to colors.primary,
        "onPrimary" to colors.onPrimary,
        "primaryContainer" to colors.primaryContainer,
        "onPrimaryContainer" to colors.onPrimaryContainer,
        "tertiary（提示）" to colors.tertiary,
        "onTertiary" to colors.onTertiary,
        "tertiaryContainer" to colors.tertiaryContainer,
        "onTertiaryContainer" to colors.onTertiaryContainer,
        "error（错误）" to colors.error,
        "onError" to colors.onError,
        "errorContainer" to colors.errorContainer,
        "onErrorContainer" to colors.onErrorContainer,
    )

    // ==================================================== 计划 §3 的五条硬约束

    @Test
    fun `光斑数量不超过 8 个`() {
        // 计划 §3 / 风险 R-1（视觉噪声）与 R-4（与棋盘动画抢帧的绘制预算）。
        val count = DefaultBackgroundSpec.blobCount
        assertTrue("光斑数量应为正数，否则「动态背景」名不副实", count > 0)
        assertTrue("光斑数量 $count 超过计划 §3 的上限 $MAX_BLOB_COUNT 个", count <= MAX_BLOB_COUNT)
    }

    @Test
    fun `单个光斑的透明度不超过 0_08`() {
        // 计划 §3 / 风险 R-1：背景元素必须弱到「看久了才注意到」。
        val spec = DefaultBackgroundSpec
        assertTrue("单个光斑的最大透明度应大于 0，否则背景不可见", spec.maxAlpha > 0.0)
        assertTrue(
            "单个光斑的最大透明度 ${spec.maxAlpha} 超过计划 §3 的上限 $MAX_ALPHA",
            spec.maxAlpha <= MAX_ALPHA,
        )

        // 光有常量断言不够：运行时用的是 backgroundBlobAlpha 的三档系数，
        // 必须确认**实际画出来的每一个光斑**都不超过上限，且上限确实被用到（不是虚设）。
        var actualMax = 0.0
        for (index in 0 until 37) {
            val alpha = backgroundBlobAlpha(spec, index)
            assertTrue("第 $index 个光斑的透明度 $alpha 应为正数", alpha > 0.0)
            assertTrue(
                "第 $index 个光斑的透明度 $alpha 超过上限 $MAX_ALPHA",
                alpha <= MAX_ALPHA,
            )
            actualMax = maxOf(actualMax, alpha)
        }
        assertTrue(
            "三档系数应至少有一档取到 maxAlpha，否则 maxAlpha 只是个装饰性数字",
            actualMax == spec.maxAlpha,
        )
    }

    @Test
    fun `漂移周期不短于 20 秒 - 且三路速度都不短于它`() {
        // 计划 §3 / 风险 R-1：周期太短会形成「节律」，吸引注意力。
        val spec = DefaultBackgroundSpec
        assertTrue(
            "基准周期 ${spec.periodMillis}ms 短于计划 §3 的下限 ${MIN_PERIOD_MILLIS}ms",
            spec.periodMillis >= MIN_PERIOD_MILLIS,
        )

        // 三路不同速度是「不要同步」的实现手段，但不能成为偷偷提速的借口：
        // 逐路复核，避免日后有人为了「更有生气」把某一档的倍率改到 1 以下。
        val periods = backgroundDriftPeriods(spec)
        assertTrue("应有三路不同速度的漂移，否则光斑会整齐划一地移动", periods.size >= 2)
        for ((index, period) in periods.withIndex()) {
            assertTrue(
                "第 $index 路漂移的周期 ${period}ms 短于下限 ${MIN_PERIOD_MILLIS}ms",
                period >= MIN_PERIOD_MILLIS,
            )
        }
        assertTrue(
            "三路周期应互不相同（本断言同时挡住「倍率被改成同一个值」）",
            periods.toSet().size == periods.size,
        )
    }

    @Test
    fun `光斑半径是相对短边的大尺寸比例`() {
        // 计划 §3 的形状约束 + §13 Q1 = A（大尺寸柔和光斑）：半径太小会像牌的轮廓（风险 R-2）。
        val range = DefaultBackgroundSpec.radiusRange
        assertTrue(
            "半径下界 ${range.start} 偏小：Q1 选定的是「大尺寸」光斑，下界应不低于 $MIN_RADIUS_RATIO",
            range.start >= MIN_RADIUS_RATIO,
        )
        assertTrue(
            "半径上界 ${range.endInclusive} 应不超过屏幕短边的 1 倍",
            range.endInclusive <= 1.0,
        )

        // 运行时取值也必须落在区间内（backgroundBlobRadius 按序号均匀铺开）。
        for (index in 0 until DefaultBackgroundSpec.blobCount) {
            val radius = backgroundBlobRadius(DefaultBackgroundSpec, index)
            assertTrue(
                "第 $index 个光斑的半径比例 $radius 不在区间 $range 内",
                radius in range,
            )
        }
    }

    @Test
    fun `半径区间上下界合法 - 且取值始终落在区间内`() {
        // 这条测的是 backgroundBlobRadius 本身的不变量：区间合法（lower <= upper、都落在 (0,1]）
        // 且任何输入下取值都不越界、也不抛异常（牌面之外的边界条件不该崩）。
        val cases = listOf(
            "默认" to DefaultBackgroundSpec,
            "上下界相等" to DefaultBackgroundSpec.copy(radiusRange = 0.5..0.5),
            "满区间" to DefaultBackgroundSpec.copy(radiusRange = 0.1..1.0),
            "单个光斑" to DefaultBackgroundSpec.copy(blobCount = 1),
            "空光斑（退化输入）" to DefaultBackgroundSpec.copy(blobCount = 0),
        )
        for ((label, spec) in cases) {
            val range = spec.radiusRange
            assertTrue("$label：半径区间下界应大于 0", range.start > 0.0)
            assertTrue("$label：半径区间上界应不超过 1", range.endInclusive <= 1.0)
            assertTrue("$label：半径区间下界不应大于上界", range.start <= range.endInclusive)

            // 含越界序号：函数应夹取而不是抛异常。
            for (index in -2..spec.blobCount + 1) {
                val radius = backgroundBlobRadius(spec, index)
                assertTrue(
                    "$label：序号 $index 的半径比例 $radius 越出区间 $range",
                    radius in range,
                )
            }
        }
    }

    @Test
    fun `背景用色不含承载游戏语义的强调色`() {
        // 计划 §3 最后一条 / 风险 R-1 / 验收项 A-2。
        // primary 承载「选中」、tertiary 承载「提示」、error 承载「错误」（V1.3 定的三套信号）；
        // 背景若从同一角色取色，会直接削弱这三个信号在棋盘上的辨识度。
        for (palette in ThemePalettes.all) {
            val colors = palette.light
            val blobColors = backgroundBlobColors(colors)
            assertTrue("主题 ${palette.id} 的背景用色不应为空", blobColors.isNotEmpty())

            val accents = semanticAccentRoles(colors)
            for ((role, accent) in accents) {
                for (blobColor in blobColors) {
                    assertTrue(
                        "主题 ${palette.id} 的背景用色 $blobColor 与强调色 $role 取到了同一个值：" +
                            "背景不得使用承载游戏语义的颜色",
                        blobColor != accent,
                    )
                }
            }
        }
    }
}
