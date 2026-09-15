package com.ldxy.lianliankan.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * 动态背景（V1.5 / 修订 2；需求见 SRS FR-13.9）。
 *
 * ### 为什么这个文件值得单独读一遍
 * 这是本项目**首次引入永不停止的动画**，也是**踩坑最深的一个文件**：两轮真机反馈都是
 * 「看不到背景」，而两次的原因都不是配色好不好看，而是**动画根本没有推进**。
 * 本文件的注释因此把「为什么不用某种写法」写得比「用了什么写法」更详细 —— 那些坑
 * 都不在代码里，而在 Compose 与系统设置的交互里。改动前请先读
 * `doc/V1.5改进计划.md` 的 §15（修订 1）与 §16（修订 2）。
 *
 * ### 时钟：为什么不用 `rememberInfiniteTransition`
 * 修订 1 用 `rememberInfiniteTransition` + `infiniteRepeatable(tween(20s))`。真机上
 * **一个光斑都看不到**，原因是系统把「动画程序时长缩放」设为 0（开发者选项里的
 * 「Animator duration scale → 关闭」，无障碍的「移除动画」也会置 0）时，Compose 会把
 * 动画时长按该缩放系数折算，而 `SuspendAnimation.doAnimationFrameWithScale` 对
 * `durationScale == 0` 的处理是**直接把播放时刻取到动画结尾**：
 *
 * ```kotlin
 * val playTimeNanos = if (durationScale == 0f) anim.durationNanos else (...)
 * ```
 *
 * 于是进度恒为 1.0（[androidx.compose.animation.core.animateFloat] 的终值），四个光斑
 * 全部停在 `RISE_START - RISE_TRAVEL = -0.30` 倍屏高 —— **屏幕上方之外**，屏幕上自然
 * 什么都没有。（同一个原因也解释了修订 0：进度恒为 1.0 时 sin/cos 的位置与 0 完全相同，
 * 光斑静止在屏内，但当时的像素差只有 1~2 级，同样看不见。）
 *
 * 本版改为**自己用 [withFrameNanos] 累计帧间隔**来推进进度：原始帧回调不经过动画时长
 * 缩放，因此不受该设置影响。这也是本项目「不读系统偏好」决策（计划 §13 Q3 = B）在
 * 实现层的自然结果 —— 背景动画就是照常运动，不论系统把动画时长缩放设成多少。
 *
 * ### 另一个副作用（好的那种）：切后台不再跳变
 * 累计的是**帧间隔**而不是绝对时间，所以前台期间没有帧时（应用在后台、或窗口不可见）
 * 进度就自然停住，回到前台接着走 —— 修订 1 的「回到前台相位从 0 重开、光斑位置跳一次」
 * 随之消失。
 *
 * ### 若门控判断出错，最坏是「不动」，而不是「消失」
 * [AnimatedBackground] 里那个前台门控**只决定时间是否推进**，不再决定画什么：
 * 光斑在静止状态下照样会被画出来（只是不动）。这是修订 2 特意设计的失败模式 ——
 * 修订 1 的「非前台就走另一个只管画渐变的分支」让一个门控判断错误变成了整层背景消失，
 * 排查时无从下手。
 */

/**
 * 动态背景的全部参数。
 *
 * 做成纯数据对象是为了让计划 §3 的硬约束**可被单测断言**：约束写在常量里、断言钉在
 * `AnimatedBackgroundTest` 里，这样「每个光斑在其峰值处都看得见」这类要求就不是注释里的一句话，
 * 而是改坏就会红的东西（计划风险 R-1 / R-4）。
 *
 * @param orbCount 光斑数量（计划 §3：≤ 8 个，控制绘制预算与视觉噪声）
 * @param maxAlpha **单个光斑在其呼吸峰值处**的透明度上限。注意它只是「配方」，
 *   真正要保证的量是合成后的像素差，见 [backgroundOrbComposite]。
 * @param riseMillis 基准上浮周期，毫秒：从屏幕下缘之外升到上缘之外所需的时间
 * @param radiusRange 光斑半径的取值范围，**相对屏幕短边的比例**（短边而非长边，
 *   才能保证横竖比例不同的设备上观感一致）
 */
data class BackgroundSpec(
    val orbCount: Int,
    val maxAlpha: Double,
    val riseMillis: Int,
    val radiusRange: ClosedRange<Double>,
)

/**
 * 实际使用的背景参数。
 *
 * ### 为什么透明度从 0.055 一路改到 0.36
 * 初版把透明度上限定在 0.08，并取了 0.055「留出余量」。真机反馈是**完全看不出背景在动**，
 * 复盘发现这个约束本身立错了地方：**「看得见」由合成后的像素差决定，而像素差 = 透明度 ×
 * （底色 − 光斑色）**。初版的光斑色取 `surfaceVariant`（#DBE4EA）与 `outlineVariant`（#BFC8CE），
 * 它们相对底色（#F8F9FC）的差只有 29 / 57 级；乘上 0.033~0.055 的透明度，实际像素差只有
 * **1~2 级（约 0.5%）**，远低于人眼在大面积色块上的察觉阈值 —— 5 个光斑里有 4 个在数学上
 * 就是不可见的。
 *
 * 因此改为**用像素差来定约束**：先定「每个光斑的峰值像素差应落在可见区间」，
 * 再由它反推透明度与用色。这条像素差约束被单测逐主题色钉住，而透明度上限只是它的副产品。
 * （**注意**：这一步是必要的，但**并不充分** —— 像素差只是必要条件，真正的阈值要靠真机定，
 * 见下一节。）
 *
 * ### 为什么最终是 0.36 —— 一次被真机推翻三次的定值过程
 * 历代取值与真机结果（「峰值像素差」= 光斑中心合成色与底色的最大单通道差；
 * 修订 0~3 的光斑都是中性灰，因此它同时就是明暗差）：
 *
 * | 版本 | 峰值透明度 | 峰值像素差 | 真机结果 |
 * |---|---|---|---|
 * | 修订 0 | 0.055 | **1~2 级（0.5%）** | 完全看不出（光斑色本身就贴近底色，乘法之后几乎为零） |
 * | 修订 1 | 0.10 | 15~20 级（6~8%） | 看不出 |
 * | 修订 2 | 0.13 | 16~26 级（6~10%） | 看不出 |
 * | 诊断版 | **0.39** | 46~75 级（18~30%） | **看得很清楚**，但评价是「灰色光斑太难看」 |
 * | 修订 3 | 0.36 | 45~72 级 | 可见性达标；**颜色被否**（灰） |
 * | **修订 4** | **0.36** | 明暗差 77~78 级 + 通道极差 20~63 级 | 改用主题色（见 [backgroundOrbColors]） |
 *
 * 修订 0~2 错在同一件事上：**把「可见性」当成了一个可以纯靠算术拍板的量。**
 * 「像素差 ≥ N 级就能看见」在本机复算里看着很合理，但那是**静态图像**的判据；
 * 真实场景是「一大块边缘极柔、移动极慢的浅灰」，人眼对它的敏感度远低于对一张图里
 * 一块均匀色斑的敏感度，何况玩家正在扫视棋盘找牌、注意力根本不在背景上。
 * 结论：**这类「氛围型」视觉参数的验收判据只能是真机 A/B，不能是计算值。**
 * 诊断版（把同一套绘制代码的透明度放大 3 倍）就是那次 A/B —— 它同时证明了
 * 「这一层在画」「位置正确」「时钟在走」，把变量收敛到了浓度这一个维度上。
 *
 * 修订 4 则是另一条教训的另一半：**可见 ≠ 好看。** 浓度调到够看见之后，颜色本身成了问题，
 * 而这一条同样只能靠真机判断（本机既渲染不了，也没有「观感」这个量）。
 *
 * | 参数 | 取值 | 依据 |
 * |---|---|---|
 * | 数量 | 4 | 参照实现是 3 个；4 个能在 360dp 宽屏上铺开又不至于是「一堆圆点」。上浮周期与初始相位都互不相同，所以不会整齐划一地移动 |
 * | 峰值透明度 | 0.36 | 见上表。主题色的相对亮度约 28.8、底色约 242，乘以 0.36 得到 **77~78 级**的明暗变化 —— 落在真机已证实可见的区间（26 级看不见 / 78 级看得清）里。上限 0.45（[MAX_ALPHA]）留出上调余地 |
 * | 上浮周期 | 20 000 ms | 参照实现是 10/12/14 s，本项目放慢到 17~29 s：仍能一眼看出在动，但不至于抢走找牌时的注意力。下限 15 s（[MIN_RISE_MILLIS]） |
 * | 半径 | 0.22 ~ 0.34 | 相对**短边**（竖屏即屏宽）。参照实现是 0.24~0.36。刻意**不做**成覆盖半屏的巨大光斑：光斑越大，同一时刻被压暗的面积越大，棋盘上「牌与背景的边界」被削弱的范围也越大（Q2 = B 没有底板，这是该决策唯一的风险点） |
 *
 * **关于棋盘的边界感**（浓度提高后重新核对过）：未选中的牌是 `surfaceVariant`（#DBE4EA，相对亮度 195），
 * 而背景在光斑中心会被压到约 174 —— **比牌更暗**，所以牌是「浅色的方块浮在彩色薄雾上」，
 * 边界反而更清楚。真正会削弱边界的是「背景恰好被压到与牌同亮度」的那一圈
 * （透明度约 0.19 处），它随光斑缓慢移动。这一圈无法消除（径向渐变的必经之处），
 * 只能通过降低峰值透明度让它更靠外、更弱。
 *
 * 上浮的起止位置、摆动幅度、呼吸深度等不出现在本对象里：它们不影响上面任何一条约束，
 * 放进来只会让「哪些数字是被单测保护的」变得含糊。
 */
val DefaultBackgroundSpec = BackgroundSpec(
    orbCount = 4,
    maxAlpha = 0.40,
    riseMillis = 20_000,
    radiusRange = 0.22..0.34,
)

/** 透明度上限（见 [DefaultBackgroundSpec] 的说明）。 */
const val MAX_ALPHA = 0.5

/** 上浮周期下限，毫秒（计划 §3；见 [DefaultBackgroundSpec] 的说明）。 */
const val MIN_RISE_MILLIS = 15_000

/**
 * 每个光斑的上浮周期倍率。
 *
 * 四路互不相同、且互不成整数倍，使各光斑不会周期性对齐 —— 否则屏幕上会出现
 * 「每隔几秒整齐一次」的节律。最小的一路是 0.85，因此**每一路都必须单独复核**
 * 是否仍不短于 [MIN_RISE_MILLIS]（见 `AnimatedBackgroundTest`）。
 */
private val RISE_PERIOD_MULTIPLIERS = listOf(1.0, 1.25, 0.85, 1.45)

/**
 * 每个光斑的透明度倍率（相对 [BackgroundSpec.maxAlpha]），让光斑之间有强弱层次。
 *
 * **最大的一档就是 1.0**：这样运行时任何光斑的峰值都不会超过 `maxAlpha`，
 * 于是「透明度上限」这条约束是自洽的，不需要额外夹取。
 *
 * 修订 4 把档位从 `0.8 / 0.9 / 1.0 / 0.85` 收紧到 `0.9 / 0.95 / 1.0 / 0.92`：
 * 改用主题色之后，明暗差的下界是按**最弱的那一路**来卡的（见单测），档位相差太大
 * 会逼着峰值透明度为最弱一路让路，结果最强的几路过浓、画面不均匀。
 */
private val ALPHA_MULTIPLIERS = listOf(0.9, 0.95, 1.0, 0.92)

/**
 * 每个光斑的**初始相位**（0~1，占一个上浮周期）。
 *
 * 这不只是「让画面丰富一点」：若四个光斑都从 0 开始，则 `t = 0` 时它们全在屏幕下缘之外，
 * 启动后的头两三秒屏幕上什么都没有。更要紧的是，**万一时间推进再次出问题，错开相位能让
 * 光斑静止地停在屏幕内**（看得见但不动），而不是全部停在屏幕外（什么都看不见）——
 * 后者的现象与「功能不存在」无法区分，排查代价极高（修订 1 就吃了这个亏）。
 */
private val RISE_PHASE_OFFSETS = listOf(0.0, 0.42, 0.71, 0.17)

/**
 * 呼吸：透明度在峰值的 [BREATH_FLOOR] ~ 1 倍之间摆动，周期是上浮周期的 3 倍（同参照实现）。
 *
 * 下限刻意**不是 0**：参照实现的呼吸会降到 0（光斑完全消失再出现），在深色底上像闪烁的
 * 光点，在浅色底上会变成「一块雾忽然出现又消失」。留底的另一个原因与浓度有关 ——
 * 峰值透明度提高到 0.36 之后，若下限仍取 0.35，光斑会在 0.13~0.36 之间起伏，
 * **恰好反复扫过「背景与未选中的牌同亮度」那一带**（约 0.13 处），于是牌的边界会周期性
 * 发糊。取 0.6 让这个起伏远离那一带（0.22 ~ 0.36）。
 */
private const val BREATH_FLOOR = 0.6
private const val BREATH_CYCLES = 3.0

/** 每个光斑的横向摆动幅度（相对屏宽）与摆动圈数。 */
private val WOBBLE_RATIOS = listOf(0.055, 0.075, 0.045, 0.065)
private const val WOBBLE_CYCLES = 2.0

/** 每个光斑的横向基准位置（相对屏宽）。 */
private val BASE_X_RATIOS = listOf(0.22, 0.62, 0.82, 0.40)

/** 上浮的起点（相对屏高）与总行程：从屏幕下缘**之外**升到上缘**之外**。 */
private const val RISE_START = 1.15
private const val RISE_TRAVEL = 1.45

/** 2π。轨迹与呼吸都用 sin 表达，进度 0 与 1 落在同一处。 */
private const val TWO_PI = 2.0 * PI

/** 一毫秒的纳秒数（[withFrameNanos] 给的是纳秒）。 */
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * 由 [spec] 推导四路上浮各自的完整周期（毫秒）。
 *
 * 之所以要推导而不是直接用 [BackgroundSpec.riseMillis]：四路**速度不同**才不会同步，
 * 但每一路都不得短于 [MIN_RISE_MILLIS] —— 否则「不同速度」就成了偷偷提速的借口。
 * 因此测试会逐路断言。
 */
fun backgroundRisePeriods(spec: BackgroundSpec = DefaultBackgroundSpec): List<Int> =
    RISE_PERIOD_MULTIPLIERS.map { (spec.riseMillis * it).toInt() }

/**
 * 第 [index] 个光斑在累计时间 [elapsedMillis] 时的进度，`0..1` 循环。
 *
 * 纯函数（只依赖入参），因此「进度确实在循环」「初始相位确实被错开」「周期非法时不崩」
 * 都能被单测覆盖 —— 这一类纯函数是本次踩坑后特意补的：把「时钟」这件事从 Composable
 * 与系统设置的交互里挪出来，挪到能被测试钉住的地方。
 *
 * @param riseMillis 该路的上浮周期；**非正数视为非法输入**，返回固定相位而不是抛异常
 *   （退化输入不该让界面崩，返回初始相位即可）。
 */
fun backgroundOrbPhase(elapsedMillis: Long, riseMillis: Int, index: Int): Double {
    val offset = RISE_PHASE_OFFSETS[index.mod(RISE_PHASE_OFFSETS.size)]
    if (riseMillis <= 0) return offset
    val cycles = elapsedMillis.toDouble() / riseMillis
    val shifted = cycles - floor(cycles) + offset
    return shifted - floor(shifted)
}

/**
 * 第 [index] 个光斑的半径，相对屏幕短边的比例。
 *
 * 在 [BackgroundSpec.radiusRange] 内**均匀铺开**且完全确定：不用随机数、不用时间，
 * 因此同一帧的绘制结果可复现（计划 §4：结果必须确定）。
 */
fun backgroundOrbRadius(spec: BackgroundSpec, index: Int): Double {
    val lastIndex = (spec.orbCount - 1).coerceAtLeast(0)
    val steps = lastIndex.coerceAtLeast(1)
    val ratio = index.coerceIn(0, lastIndex).toDouble() / steps
    val lower = spec.radiusRange.start
    return lower + (spec.radiusRange.endInclusive - lower) * ratio
}

/**
 * 第 [index] 个光斑在**呼吸峰值**处的透明度。
 *
 * 保证落在 `(0, maxAlpha]` 内：倍率最大就是 1.0（见 [ALPHA_MULTIPLIERS]），
 * 因此运行时永远不会超过 [BackgroundSpec.maxAlpha]，而该值又被单测钉在 ≤ [MAX_ALPHA]。
 */
fun backgroundOrbPeakAlpha(spec: BackgroundSpec, index: Int): Double =
    spec.maxAlpha * ALPHA_MULTIPLIERS[index.mod(ALPHA_MULTIPLIERS.size)]

/**
 * 呼吸系数：把峰值透明度按这个系数缩放即为当前透明度。
 *
 * 纯函数、只依赖进度，因此「呼吸不会让光斑完全消失」这件事可以被单测覆盖。
 */
fun backgroundOrbBreath(phase: Double): Double {
    val wave = 0.5 + 0.5 * sin(TWO_PI * phase * BREATH_CYCLES)
    return BREATH_FLOOR + (1.0 - BREATH_FLOOR) * wave
}

/**
 * 背景光斑的可用颜色（修订 4：由中性灰改为**主题色**）。
 *
 * ### 为什么不再是中性色
 * 修订 3 用 `onSurfaceVariant` / `onSurface` 两个中性角色，把浓度调到 0.36 后真机上确实
 * 清晰可见了，但开发者的评价是**「灰色光斑太难看」** —— 在一个近乎白、且本身没有色相的
 * 底色上，灰云只会把画面弄脏，而不会形成氛围。因此改用主题色，让 4 套主题色各自呈现
 * 自己的色相（浅蓝 / 黄绿 / 玫红 / 琥珀）。
 *
 * ### 为什么是 `primary` + `tertiary`，而不是别的角色
 * 逐角色实测相对亮度（`background` 约 242，tone 40 的三个强调色都在 28~29）：
 *
 * | 候选 | 相对亮度 | 能否用 | 原因 |
 * |---|---|---|---|
 * | `primary` / `tertiary` | 28.5~28.9 | **用** | 与底色差 213~215 级，乘 0.36 后仍有 **77~78 级**的明暗变化，落在真机已证实可见的区间里；色相饱和（合成后的通道极差 20~63 级），不会退化成灰 |
 * | `secondary` | 28.7 | 不用 | 明暗够，但它是**低彩度**（chroma 16）的强调色：合成后通道极差只有约 15 级，看上去仍是一片灰 —— 正是本次要摆脱的东西 |
 * | `error` | 28.7 | **禁止** | 红色在连连看里就是「错误」的语义色（FR-4.4 / FR-4.5 的红色抖动与描边），背景飘红云会被读成出错 |
 * | `*Container` | 130~195 | 不用 | 它们是 tone 90 的浅色，与近白底色的差只有 30~113 级；且彩度受明度限制，要在近白底上叠出足够明暗差就得叠到接近不透明 —— 那时它已经不像「氛围」而像一块色块 |
 *
 * ### 与游戏状态色的冲突：为什么这次可以接受
 * 修订 3 之前，本文件刻意**不用**任何强调色，理由是「`primary` 承载选中、`tertiary` 承载提示」。
 * 改用主题色之后就必然共用色相了，因此逐条复核：
 *
 * - **底色是 0.36 的半透明薄雾，不是实色。** 选中/提示/错误的**描边**用的是 tone 40 实色
 *   （相对亮度 28.8），与薄雾（约 174）的对比度仍有约 **4.6:1** —— 描边依旧清晰。
 * - **牌本身是不透明的**（`TileFaceBox` 的 `*Container` 底色），薄雾盖不到它。
 * - 受影响的只有「牌与薄雾之间」的明暗关系，而薄雾把背景压得比牌**更暗**，
 *   因此牌是「浅色方块浮在彩色薄雾上」，而不是融进背景（见 `DefaultBackgroundSpec`）。
 *
 * 结论：可以用 `primary` / `tertiary`，但 `error` 系仍然禁止 —— 这条由单测钉住
 * （`背景用色必须取自主题色 - 不得退化成灰`）。
 */
fun backgroundOrbColors(colors: ColorScheme): List<Color> = listOf(
    colors.primary,
    colors.tertiary,
)

/**
 * 把透明度为 [alpha] 的 [orb] 色叠在 [base] 色之上，返回合成后的颜色。
 *
 * 这个函数**不被绘制代码调用**（真正的合成由 Skia 完成），它的存在只有一个目的：
 * 让「光斑看得见吗」成为一个**可断言**的量。修订 0 把可见性寄托在「透明度 ≤ 0.08」
 * 这样一个配方数字上，结果 5 个光斑里有 4 个在数学上根本不可见，而当时的测试全绿 ——
 * 因为测试只检查了配方，没有检查成品。
 *
 * 按 sRGB 通道线性插值，与实际合成路径一致（两个输入色都是不透明的）。
 */
fun backgroundOrbComposite(base: Color, orb: Color, alpha: Double): Color {
    val t = alpha.toFloat()
    return Color(
        red = base.red + (orb.red - base.red) * t,
        green = base.green + (orb.green - base.green) * t,
        blue = base.blue + (orb.blue - base.blue) * t,
        alpha = 1f,
    )
}

/**
 * 应用背景：静态竖向渐变 + 缓缓上浮、左右摆动、明暗呼吸的光斑
 * （V1.5 / SRS FR-13.9；原有渐变见 FR-13.8）。
 *
 * 由 `MainActivity` 铺满全屏地放在所有导航内容**之下**（Box 的子级按声明顺序绘制，
 * 因此它必须是第一个子级）。
 *
 * **注意**：它上面任何一层只要画了不透明底色，这层动画就整层看不见 ——
 * `Scaffold` 的默认 `containerColor` 正是 `colorScheme.background`（不透明），
 * 关卡选择与设置两屏因此在修订 0 里完全看不到背景（修订 1 已改为透明）。
 * 新增界面时请留意同一件事。
 *
 * @param modifier 由调用方决定铺多大；未指定时也会铺满父容器。
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    // 前台门控**只决定时间是否推进**（见文件头「若门控判断出错」）。
    // 因此即使这里判断错了，屏幕上也仍然有静止的光斑，而不是一片空白。
    val advancing = rememberIsResumed()
    val elapsedMillis = rememberElapsedMillis(advancing)

    val spec = DefaultBackgroundSpec
    // 基底仍然是 V1.4 的那层竖向渐变（计划 §4 第 1 条：光斑叠加在其上），
    // 因此在 4 套主题色下背景的基调与 V1.4 保持一致。
    val background = appBackgroundBrush()
    // 颜色只能取自中性色角色（见 backgroundOrbColors 的说明）。
    val orbColors = backgroundOrbColors(MaterialTheme.colorScheme)
    val periods = backgroundRisePeriods(spec)

    Canvas(modifier = modifier.fillMaxSize()) {
        // 零尺寸时半径无效（径向渐变不允许半径 ≤ 0），此时也没有任何东西可画。
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        drawRect(brush = background)

        // ★ 时间在这里读：draw 阶段（计划 §4 第 1 条 / 风险 R-5）。
        // 读在组合期（例如在函数体里写 val t = elapsedMillis.value）会让每一帧都重组
        // 整棵子树 —— 这是本项目首次引入常驻动画，做错代价很大。
        val elapsed = elapsedMillis.value
        val shortSide = min(size.width, size.height)

        for (index in 0 until spec.orbCount) {
            val phase = backgroundOrbPhase(elapsed, periods[index.mod(periods.size)], index)

            val radius = shortSide * backgroundOrbRadius(spec, index).toFloat()

            // 横向：基准位置 + 正弦摆动（2 圈）。
            val wobble = WOBBLE_RATIOS[index.mod(WOBBLE_RATIOS.size)]
            val centerX = size.width *
                (BASE_X_RATIOS[index.mod(BASE_X_RATIOS.size)] +
                    wobble * sin(TWO_PI * (phase * WOBBLE_CYCLES)))

            // 纵向：从屏幕下缘之外匀速升到上缘之外。
            val centerY = size.height * (RISE_START - RISE_TRAVEL * phase)
            val center = Offset(centerX.toFloat(), centerY.toFloat())

            // 呼吸：透明度在峰值的 60%~100% 之间摆动（见 BREATH_FLOOR）。
            val alpha = (backgroundOrbPeakAlpha(spec, index) * backgroundOrbBreath(phase)).toFloat()
            val color = orbColors[index.mod(orbColors.size)]

            // 圆心与半径同时交给画笔和 drawCircle —— 两者必须一致，否则渐变会偏离光斑。
            // 这里刻意**不**用 withTransform 平移画布（修订 1 的写法）：那种写法依赖
            // 「画布变换同样作用于画笔着色器」这一条本机无法验证的平台行为，
            // 而本机是虚拟化环境、起不了模拟器，没有别的办法验证。
            // 中间档略高于线性：边缘更快收敛到 0，光斑不出现可辨的圆形硬边（风险 R-2）。
            // 最外圈用同色零透明而不是 Color.Transparent（后者是「透明黑」，会把边缘染灰）。
            drawCircle(
                brush = Brush.radialGradient(
                    // 三档等距分布（0.0 / 0.5 / 1.0）。
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.55f),
                        color.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/**
 * 累计「动画已经走了多少毫秒」，**只在前台推进**。
 *
 * ### 为什么自己数帧，而不用 `rememberInfiniteTransition`
 * 见文件头：Compose 的动画会把时长按系统的「动画程序时长缩放」折算，缩放为 0 时
 * 进度会被直接取到动画结尾，于是光斑全部停在屏幕之外。这里用的是 [withFrameNanos]
 * 的**原始帧时间**，不经过任何时长缩放，因此系统设成多少都不影响。
 *
 * ### 为什么累计帧间隔，而不是用「当前时间 − 起始时间」
 * 累计帧间隔意味着**没有帧的时候时间就不走**：应用切到后台（或窗口不可见）时，
 * 平台不再派发帧，进度自然停住，回到前台接着走。用绝对时间的话，切回来的瞬间
 * 相位会跳一大段（修订 1 的现象）。
 *
 * @param advancing 为 false 时立即返回、不请求任何帧，于是进度冻结。
 */
@Composable
private fun rememberElapsedMillis(advancing: Boolean): State<Long> {
    val elapsed = remember { mutableStateOf(0L) }
    LaunchedEffect(advancing) {
        if (!advancing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsed.value += (now - last) / NANOS_PER_MILLI
            last = now
        }
    }
    return elapsed
}

/**
 * 当前是否处于 `Lifecycle.State.RESUMED`（前台可见且可交互）。
 *
 * ### 为什么不用 `LifecycleResumeEffect`（修订 2）
 * 修订 1 用的是 `LifecycleResumeEffect`。它本身没有问题 —— `LifecycleRegistry.addObserver`
 * 在注册时会把新观察者**补发**到当前状态（`addObserver` 里那段
 * `while (statefulObserver.state < targetState) dispatchEvent(upFrom(...))`），
 * 所以已 RESUMED 时订阅也能收到 `ON_RESUME`。修订 2 换成手写观察者，只是为了让
 * 「初值取自 `currentState`」这件事**显式可见**：不依赖补发行为，也就不需要读者去确认它。
 *
 * 另外这里多订阅了 `ON_STOP`：`ON_PAUSE` 已足够表达「不再可见」，但部分机型在
 * 分屏/画中画下会先 STOP 再 PAUSE，两处都置假更保险（置假只是让动画冻结，不画错东西）。
 */
@Composable
private fun rememberIsResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isResumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isResumed = false
                else -> Unit
            }
        }
        // 订阅前先按当前状态取一次初值：这样即使框架不补发 ON_RESUME，也不会漏掉。
        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return isResumed
}
