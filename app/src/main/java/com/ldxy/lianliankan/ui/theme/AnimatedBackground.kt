package com.ldxy.lianliankan.ui.theme

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 动态背景（V1.5；需求见 SRS FR-13.9）。
 *
 * ### 为什么这个文件值得单独读一遍
 * 这是本项目**首次引入永不停止的动画**：V1.5 之前的全部动画都是触发式的有限时长
 * （连线 150ms、消除 250ms、抖动 320ms），游戏界面总会回到「无动画」状态。加了动态背景后
 * 不再如此，功耗与发热特性发生实质变化。因此本文件里的每条实现决策都对应
 * `doc/V1.5改进计划.md` 的一个约束或风险编号，注释里会逐条点名 —— 改动前请先读该计划的
 * §3（设计约束）、§4（技术方案）、§5（性能与省电）、§8（风险表）与 §14.5（修订 1）。
 *
 * ### 视觉构想：几个半透明光斑缓缓上浮并左右摆动
 * 形态与节奏参照开发者另一个项目中的 `FloatingOrbs`（`Birthday` 项目，
 * `ui/components/FloatingOrbs.kt`）：光斑从屏幕下缘之外升到上缘之外，同时左右轻微摆动，
 * 透明度还有一层更快的「呼吸」，整体是缓慢流动的氛围，而不是一堆静止的圆点。
 *
 * 两处必须偏离参照实现的地方（都源于本项目的底色与参照项目相反）：
 * - **明暗关系反过来。** 参照项目的底色是饱和的深紫—品红渐变，光斑用白色（比底色**亮**）；
 *   本项目的底色是近乎白的浅色渐变（`background` #F8F9FC），比它更亮的颜色不存在，
 *   因此光斑只能用**比底色暗**的中性色。观感因此是「淡雾上浮」而不是「白色光球」——
 *   这是浅色主题下的必然结果，不是实现走样。
 * - **透明度必须更大。** 参见 [DefaultBackgroundSpec]：「看得见」取决于**合成后的像素差**，
 *   而不是透明度数值本身；在近白底上达到与参照项目相当的像素差需要更大的透明度。
 *
 * ### 本文件与计划的三处差异（决策 Q2/Q3 的连带影响）
 * - 计划 §13 Q2 = B（**不给棋盘加底板**）：本文件里没有任何棋盘侧的改动，§9 的验收项 V-7 作废。
 * - 计划 §13 Q3 = B（**不读系统「减少动效」偏好**）：因此没有 `ANIMATOR_DURATION_SCALE` 判定，
 *   计划 §6 整节作废、验收项 A-3 与 V-6 作废。这是一个**有意识接受的无障碍退让**，
 *   已记入 README 的「已知取舍」表。下面 [StaticBackground] 这个静止分支正好是日后
 *   恢复该功能所需的落点（成本约十行），因此它虽然只为生命周期门控服务，仍然独立保留。
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
 * ### 为什么透明度是 0.10，而不是初版的 0.055
 * 初版把透明度上限定在 0.08，并取了 0.055「留出余量」。真机反馈是**完全看不出背景在动**，
 * 复盘发现这个约束本身立错了地方：**「看得见」由合成后的像素差决定，而像素差 = 透明度 ×
 * （底色 − 光斑色）**。初版的光斑色取 `surfaceVariant`（#DBE4EA）与 `outlineVariant`（#BFC8CE），
 * 它们相对底色（#F8F9FC）的差只有 29 / 57 级；乘上 0.033~0.055 的透明度，实际像素差只有
 * **1~2 级（约 0.5%）**，远低于人眼在大面积色块上的察觉阈值 —— 5 个光斑里有 4 个在数学上
 * 就是不可见的，剩下一个（`onSurfaceVariant`，184 级差）中心处也只有 10 级。
 *
 * 因此本版改为**用像素差来定约束**：先定「每个光斑的峰值像素差应落在 12~28 级」，
 * 再由它反推透明度与用色。这条像素差约束被单测逐主题色钉住，而透明度上限只是它的副产品。
 *
 * | 参数 | 取值 | 依据 |
 * |---|---|---|
 * | 数量 | 4 | 参照实现是 3 个；4 个能在 360dp 宽屏上铺开又不至于是「一堆圆点」。上浮周期互不相同，所以不会整齐划一地移动 |
 * | 峰值透明度 | 0.10 | 乘以用色与底色的差（223~226 级 / 184~193 级）得到峰值像素差 **14.7~20.3 级（约 6~8%）**：明显可见，又远不到「刺眼」。上限 0.12（[MAX_ALPHA]）留出了上调余地 |
 * | 上浮周期 | 20 000 ms | 参照实现是 10/12/14 s，本项目放慢到 17~29 s：仍能一眼看出在动，但不至于抢走找牌时的注意力。下限 15 s（[MIN_RISE_MILLIS]） |
 * | 半径 | 0.22 ~ 0.34 | 相对**短边**（竖屏即屏宽）。参照实现是 0.24~0.36。刻意**不做**成覆盖半屏的巨大光斑：光斑越大，同一时刻被压暗的面积越大，棋盘上「牌与背景的边界」被削弱的范围也越大（Q2 = B 没有底板，这是该决策唯一的风险点） |
 *
 * 上浮的起止位置、摆动幅度、呼吸深度等不出现在本对象里：它们不影响上面任何一条约束，
 * 放进来只会让「哪些数字是被单测保护的」变得含糊。它们以文件级常量给出并各自带注释。
 */
val DefaultBackgroundSpec = BackgroundSpec(
    orbCount = 4,
    maxAlpha = 0.10,
    riseMillis = 20_000,
    radiusRange = 0.22..0.34,
)

/** 透明度上限（计划 §3；见 [DefaultBackgroundSpec] 的说明）。 */
const val MAX_ALPHA = 0.12

/** 上浮周期下限，毫秒（计划 §3；见 [DefaultBackgroundSpec] 的说明）。 */
const val MIN_RISE_MILLIS = 15_000

/**
 * 每个光斑的上浮周期倍率。
 *
 * 四路互不相同、且互不成整数倍，使各光斑不会周期性对齐 —— 否则屏幕上会出现
 * 「每隔几秒整齐一次」的节律。最小的一路是 0.85，因此**每一路都必须单独复核**
 * 是否仍不短于 [MIN_RISE_MILLIS]（见 `AnimatedBackgroundTest`）。
 */
private val RISE_PERIOD_MULTIPLIERS = listOf(1.0f, 1.25f, 0.85f, 1.45f)

/**
 * 每个光斑的透明度倍率（相对 [BackgroundSpec.maxAlpha]），让光斑之间有强弱层次。
 *
 * **最大的一档就是 1.0**：这样运行时任何光斑的峰值都不会超过 `maxAlpha`，
 * 于是「透明度上限」这条约束是自洽的，不需要额外夹取。
 */
private val ALPHA_MULTIPLIERS = listOf(0.8, 0.9, 1.0, 0.85)

/**
 * 呼吸：透明度在峰值的 [BREATH_FLOOR] ~ 1 倍之间摆动，周期是上浮周期的 3 倍（同参照实现）。
 *
 * 下限刻意**不是 0**：参照实现的呼吸会降到 0（光斑完全消失再出现），在深色底上像闪烁的
 * 光点，在浅色底上会变成「一块雾忽然出现又消失」。留 35% 的底，光斑始终隐约在场。
 */
private const val BREATH_FLOOR = 0.35
private const val BREATH_CYCLES = 3.0

/** 每个光斑的横向摆动幅度（相对屏宽）与摆动圈数。 */
private val WOBBLE_RATIOS = listOf(0.055, 0.075, 0.045, 0.065)
private const val WOBBLE_CYCLES = 2.0

/** 每个光斑的横向基准位置（相对屏宽）与相位偏移（圈）。相位错开，光斑才不会同步摆。 */
private val BASE_X_RATIOS = listOf(0.22, 0.62, 0.82, 0.40)
private val PHASE_OFFSETS = listOf(0.0, 0.31, 0.57, 0.79)

/** 上浮的起点（相对屏高）与总行程：从屏幕下缘**之外**升到上缘**之外**。 */
private const val RISE_START = 1.15
private const val RISE_TRAVEL = 1.45

/** 2π。轨迹与呼吸都用 sin 表达，进度 0 与 1 落在同一处 —— 循环处天然无缝。 */
private const val TWO_PI = 2.0 * PI

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
 * 第 [index] 个光斑的呼吸系数：把峰值透明度按这个系数缩放即为当前透明度。
 *
 * 纯函数、只依赖进度，因此「呼吸不会让光斑完全消失」这件事可以被单测覆盖。
 */
fun backgroundOrbBreath(index: Int, phase: Double): Double {
    val offset = PHASE_OFFSETS[index.mod(PHASE_OFFSETS.size)]
    val wave = 0.5 + 0.5 * sin(TWO_PI * (phase * BREATH_CYCLES + offset))
    return BREATH_FLOOR + (1.0 - BREATH_FLOOR) * wave
}

/**
 * 背景光斑的可用颜色（计划 §3 最后一条 / 风险 R-1）。
 *
 * **只返回中性色角色**：`primary` 承载「选中」、`tertiary` 承载「提示」、`error` 承载「错误」，
 * 而三个 `*Container` 角色正是牌的底色（见 `TileFaceBox`）—— 背景若从这些角色取色，
 * 会直接削弱棋盘上的状态信号与牌的辨识度。因此这里连「可以传强调色」的口子都不留。
 *
 * 选 `onSurfaceVariant`（tone 30）与 `onSurface`（tone 10）这两个**中性**角色，是因为它们
 * 与近白的底色之间有 184~226 级的差，乘上 0.08~0.10 的透明度正好落在可见区间；
 * 更浅的中性角色（`surfaceVariant` / `outlineVariant`）在数学上无法被看见
 * （详见 [DefaultBackgroundSpec] 的复盘）。两个角色都为中性，却各自带着本主题色的色相
 * （例如玫红主题下是深玫瑰灰），于是光斑之间既有层次，又随主题色变化而不是写死的灰色。
 *
 * 返回值**恒非空**（调用方按 `index % size` 取用）；4 套主题色下都成立，因为颜色由
 * 当前 [ColorScheme] 推导而非写死。
 */
fun backgroundOrbColors(colors: ColorScheme): List<Color> = listOf(
    colors.onSurfaceVariant,
    colors.onSurface,
)

/**
 * 把透明度为 [alpha] 的 [orb] 色叠在 [base] 色之上，返回合成后的颜色。
 *
 * 这个函数**不被绘制代码调用**（真正的合成由 Skia 完成），它的存在只有一个目的：
 * 让「光斑看得见吗」成为一个**可断言**的量。V1.5 初版把可见性寄托在「透明度 ≤ 0.08」
 * 这样一个配方数字上，结果 5 个光斑里有 4 个在数学上根本不可见，而当时的测试全绿 ——
 * 因为测试只检查了配方，没有检查成品（计划 §14.5）。
 * `AnimatedBackgroundTest` 用本函数逐主题色、逐光斑断言峰值像素差落在可见区间。
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
 * 应用背景：静态竖向渐变 + 缓缓上浮的半透明光斑（V1.5 / SRS FR-13.9；原有渐变见 FR-13.8）。
 *
 * 由 `MainActivity` 铺满全屏地放在所有导航内容**之下**（Box 的子级按声明顺序绘制，
 * 因此它必须是第一个子级）。
 *
 * **注意**：它上面任何一层只要画了不透明底色，这层动画就整层看不见 ——
 * `Scaffold` 的默认 `containerColor` 正是 `colorScheme.background`（不透明），
 * 关卡选择与设置两屏因此在 V1.5 初版里完全看不到背景（V1.5 修订 1 已改为透明）。
 * 新增界面时请留意同一件事。
 *
 * @param modifier 由调用方决定铺多大；未指定时也会铺满父容器。
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    // 生命周期门控（计划 §5 第一条）：只有前台 RESUMED 才组合出「会动」的那一层。
    // 非前台的实现方式是**把动画所在的组合整段移除**，于是 rememberInfiniteTransition 的
    // 协程随节点一同被取消 —— 不是「画一帧同位置」那种假停，而是真的不再请求任何帧。
    // 详见 [rememberIsResumed] 与 [RisingOrbs] 的注释。
    if (rememberIsResumed()) {
        RisingOrbs(modifier = modifier)
    } else {
        StaticBackground(modifier = modifier)
    }
}

/**
 * 静止分支：只画 V1.4 的那层竖向渐变。
 *
 * 目前它服务于两件事：① 应用不在前台时降级（计划 §5）；② 若日后要恢复「尊重系统减少动效」
 * （计划 §6 / §13 Q3 已决定本版本不做），这里就是现成的落点 —— 那时只需在
 * [rememberIsResumed] 之外再与一个「是否允许动效」的布尔值相与即可，无需再写绘制代码。
 */
@Composable
private fun StaticBackground(modifier: Modifier) {
    val background = appBackgroundBrush()
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(brush = background)
    }
}

/**
 * 会动的那一层：基底渐变 + 若干缓缓上浮、左右摆动、明暗呼吸的光斑。
 *
 * ### 动画状态在哪里读（计划 §4 第 1 条 / 风险 R-5）
 * 四个进度值都是 `State<Float>`，**只在 [Canvas] 的 draw lambda 内被读取**，组合期一次都不读。
 * `Canvas` 底层就是 `drawBehind`：draw 阶段读快照状态只会让**绘制阶段**失效，不会触发重组；
 * 若读在组合期（例如在函数体里写 `val p = prog0.value`），那么每帧都会重组整棵子树 ——
 * 这是本项目首次引入常驻动画，做错代价很大。
 *
 * ### 画笔位置为什么不靠画布平移
 * V1.5 初版把画笔圆心固定在原点、再用 `withTransform { translate(...) }` 把画布挪到光斑位置，
 * 好处是画笔可以跨帧复用。本版改为**按光斑实际圆心创建画笔**（[Brush.radialGradient] 的
 * `center` 与 `drawCircle` 的 `center` 传同一个值），代价是每帧新建 4 个画笔，收益是
 * **不依赖「画布变换是否作用于画笔着色器」这一条无法在本机验证的平台行为**
 * （本机是虚拟化环境，起不了模拟器，也就无法通过渲染来验证；见计划 §14.5）。
 * 每帧 4 个短命小对象的分配量可忽略，而风险归零 —— 这个交换在本项目的条件下是划算的。
 *
 * ### 为什么用正弦/余弦
 * 进度是 0→1 的循环（`RepeatMode.Restart`）。上浮方向本身在循环处是断的（从顶部跳回底部），
 * 但**起止点都在屏幕之外**（[RISE_START] 与 [RISE_TRAVEL] 保证进度 0 时在屏幕下缘之下、
 * 进度 1 时在上缘之上），因此这个跳变看不到 —— 循环天然无缝，不需要额外处理。
 * 横向摆动与呼吸则用 sin 表达，它们在循环处本来就连续。
 */
@Composable
private fun RisingOrbs(modifier: Modifier) {
    val spec = DefaultBackgroundSpec

    // 基底仍然是 V1.4 的那层竖向渐变（计划 §4 第 1 条：光斑叠加在其上），
    // 因此在 4 套主题色下背景的基调与 V1.4 保持一致。
    val background = appBackgroundBrush()
    // 颜色只能取自中性色角色（见 backgroundOrbColors 的说明）。
    val orbColors = backgroundOrbColors(MaterialTheme.colorScheme)

    // 常驻动画的时钟：**一个** transition 驱动四路不同速度的进度。
    // 计划 §4 第 2 条要求参数集中 —— 若每个光斑各配一个 transition，
    // Compose 就要维护 N 套动画状态与帧回调，纯属浪费（本项目的常驻动画应是最轻的那个）。
    val transition = rememberInfiniteTransition(label = "背景光斑")
    val periods = backgroundRisePeriods(spec)
    val progress0 = transition.riseProgress(periods[0], label = "上浮-1")
    val progress1 = transition.riseProgress(periods[1], label = "上浮-2")
    val progress2 = transition.riseProgress(periods[2], label = "上浮-3")
    val progress3 = transition.riseProgress(periods[3], label = "上浮-4")
    val progresses = listOf(progress0, progress1, progress2, progress3)

    Canvas(modifier = modifier.fillMaxSize()) {
        // 零尺寸时半径无效（径向渐变不允许半径 ≤ 0），此时也没有任何东西可画。
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        drawRect(brush = background)

        val shortSide = min(size.width, size.height)

        for (index in 0 until spec.orbCount) {
            // ★ 动画状态在这里读：draw 阶段（见函数头注释与计划 §4 第 1 条 / 风险 R-5）。
            val phase = progresses[index.mod(progresses.size)].value

            val radius = shortSide * backgroundOrbRadius(spec, index).toFloat()

            // 横向：基准位置 + 正弦摆动（2 圈）。
            val wobble = WOBBLE_RATIOS[index.mod(WOBBLE_RATIOS.size)]
            val phaseOffset = PHASE_OFFSETS[index.mod(PHASE_OFFSETS.size)]
            val centerX = size.width *
                (BASE_X_RATIOS[index.mod(BASE_X_RATIOS.size)] +
                    wobble * sin(TWO_PI * (phase * WOBBLE_CYCLES + phaseOffset)))

            // 纵向：从屏幕下缘之外匀速升到上缘之外。
            val centerY = size.height * (RISE_START - RISE_TRAVEL * phase)
            val center = Offset(centerX.toFloat(), centerY.toFloat())

            // 呼吸：透明度在峰值的 35%~100% 之间摆动（见 BREATH_FLOOR）。
            val alpha = (backgroundOrbPeakAlpha(spec, index) *
                backgroundOrbBreath(index, phase.toDouble())).toFloat()
            val color = orbColors[index.mod(orbColors.size)]

            // 圆心与半径同时交给画笔和 drawCircle —— 两者必须一致，否则渐变会偏离光斑
            // （这正是本版不用画布平移的原因，见函数头注释）。
            // 中间档略高于线性：边缘更快收敛到 0，光斑不出现可辨的圆形硬边（风险 R-2：
            // 不得像任何具象形状）。最外圈用同色零透明而不是 Color.Transparent
            // （后者是「透明黑」，渐变插值会把边缘染灰）。
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
 * 当前是否处于 `Lifecycle.State.RESUMED`（前台可见且可交互）。
 *
 * [LifecycleResumeEffect] 是 `repeatOnLifecycle(Lifecycle.State.RESUMED)` 的 composable 形式：
 * `ON_RESUME` 时执行 effect、`ON_PAUSE`（或离开组合）时执行 `onPauseOrDispose`。
 * 用它的好处是「订阅—退订」由 Composable 的生命周期保证，不会漏掉退订。
 *
 * 返回 false 时 [AnimatedBackground] 会走静止分支，**连 InfiniteTransition 都不创建**，
 * 因此回到前台时会从进度 0 重新开始。由于光斑的起止点都在屏幕之外，这次重启表现为
 * 「所有光斑同时从屏幕下方重新升起」，而不是某一帧位置突变 —— 但仍与切走前的位置对不上，
 * 透明度也只有 0.03~0.10，因此列为真机观察项 V-5。若要彻底消除，需要额外记录相位基准
 * 再折算回进度（约十行）。
 */
@Composable
private fun rememberIsResumed(): Boolean {
    var isResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        isResumed = true
        onPauseOrDispose { isResumed = false }
    }
    return isResumed
}

/**
 * 为一个 [InfiniteTransition] 挂一路匀速循环的 0→1 进度。
 *
 * 用 `LinearEasing` 让进度匀速推进：上浮就该是匀速的飘，缓动只会让它忽快忽慢、更引人注意。
 */
@Composable
private fun InfiniteTransition.riseProgress(durationMillis: Int, label: String): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = label,
    )
