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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 动态背景（V1.5 本版本；需求见 SRS FR-13.9）。
 *
 * ### 为什么这个文件值得单独读一遍
 * 这是本项目**首次引入永不停止的动画**：V1.5 之前的全部动画都是触发式的有限时长
 * （连线 150ms、消除 250ms、抖动 320ms），游戏界面总会回到「无动画」状态。加了动态背景后
 * 不再如此，功耗与发热特性发生实质变化。因此本文件里的每条实现决策都对应
 * `doc/V1.5改进计划.md` 的一个约束或风险编号，注释里会逐条点名 —— 改动前请先读该计划的
 * §3（设计约束）、§4（技术方案）、§5（性能与省电）与 §8（风险表）。
 *
 * ### 视觉构想（计划 §13 Q1 = A）
 * 若干个大尺寸、极低透明度的**柔和光斑**沿椭圆轨迹缓慢漂移，叠加在原有的竖向渐变之上。
 * 不含锐利边缘、不像任何具象物体，因此最不容易与牌混淆（风险 R-2）。
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
 * `AnimatedBackgroundTest` 里，这样「透明度 ≤ 0.08」这类要求就不是注释里的一句话，
 * 而是改坏就会红的东西（计划风险 R-1 / R-4）。
 *
 * @param blobCount 光斑数量（计划 §3：≤ 8 个，控制绘制预算与视觉噪声）
 * @param maxAlpha 单个光斑的最大透明度（计划 §3：≤ 0.08）
 * @param periodMillis 一个完整漂移周期，毫秒（计划 §3：≥ 20 s）
 * @param radiusRange 光斑半径的取值范围，**相对屏幕短边的比例**（短边而非长边，
 *   才能保证横竖比例不同的设备上观感一致）
 */
data class BackgroundSpec(
    val blobCount: Int,
    val maxAlpha: Double,
    val periodMillis: Int,
    val radiusRange: ClosedRange<Double>,
)

/**
 * 实际使用的背景参数。
 *
 * 取值都**刻意小于**计划 §3 的硬上限，理由如下（计划 §11 说本项主要成本在真机调参，
 * 留出余量意味着调参时不必先越过约束线）：
 *
 * | 参数 | 取值 | 硬约束 | 为什么取这个值 |
 * |---|---|---|---|
 * | 数量 | 5 | ≤ 8 | 3 个偏空、8 个接近预算上限；5 个在 360dp 宽屏上已能铺满且互不完全重合 |
 * | 透明度 | 0.055 | ≤ 0.08 | 约上限的 2/3。单帧多光斑重叠处的合成透明度会高于单个光斑，留出这段余量正是为此；且计划 §13 Q2 明确「若真机觉得边界感不稳，最省事的补救是先降透明度」 |
 * | 周期 | 30 000 ms | ≥ 20 000 | 取下限的 1.5 倍。周期越短越容易形成「节律」被眼睛抓住（§3 第一条约束的理由），而 V-1 的验收标准是「第一眼不会注意到它在动」 |
 * | 半径 | 0.35 ~ 0.70 | — | 相对**短边**的大尺寸：360dp 宽的设备上半径 126~252dp。Q1 选定的是「大尺寸柔和光斑」，半径太小会退化成可数的圆点、反而更像牌的轮廓 |
 */
val DefaultBackgroundSpec = BackgroundSpec(
    blobCount = 5,
    maxAlpha = 0.055,
    periodMillis = 30_000,
    radiusRange = 0.35..0.70,
)

/**
 * 三路漂移速度相对基准周期的倍率。
 *
 * 取**互不成整数倍**的三个值，使三路进度不会周期性重合 —— 否则屏幕上会出现一个
 * 「每隔几秒整齐一次」的节律，恰好是计划 §3 第一条约束要避免的东西。
 */
private val DRIFT_SPEED_MULTIPLIERS = listOf(1.0f, 1.31f, 1.73f)

/** 光斑透明度相对 [BackgroundSpec.maxAlpha] 的三档系数（循环使用），让光斑之间有层次。 */
private val ALPHA_STEPS = doubleArrayOf(0.6, 0.8, 1.0)

/** 2π。光斑轨迹用 sin/cos 表达，因此进度 0 与 1 落在同一点 —— 循环处天然无缝。 */
private const val TWO_PI = 2.0 * PI

/** 光斑中心在横/纵方向上的漂移幅度，相对屏幕宽/高的比例。 */
private const val WANDER_X = 0.30
private const val WANDER_Y = 0.26

/** 相邻光斑的初始相位错开步长（单位：圈）。取无理数般的非整齐值，避免光斑同步移动。 */
private const val PHASE_X_STEP = 0.37
private const val PHASE_Y_STEP = 0.61

/**
 * 由 [spec] 推导三路漂移各自的完整周期（毫秒）。
 *
 * 之所以要推导而不是直接用 [BackgroundSpec.periodMillis]：三路**速度不同**才不会同步，
 * 但每一路都不得短于计划 §3 的 20 s 下限 —— 否则「不同速度」就成了偷偷提速的借口。
 * 因此测试会逐路断言（见 `AnimatedBackgroundTest`）。
 */
fun backgroundDriftPeriods(spec: BackgroundSpec = DefaultBackgroundSpec): List<Int> =
    DRIFT_SPEED_MULTIPLIERS.map { (spec.periodMillis * it).toInt() }

/**
 * 第 [index] 个光斑的半径，相对屏幕短边的比例。
 *
 * 在 [BackgroundSpec.radiusRange] 内**均匀铺开**且完全确定：不用随机数、不用时间，
 * 因此同一帧的绘制结果可复现（计划 §4 第 5 条：结果必须确定）。
 */
fun backgroundBlobRadius(spec: BackgroundSpec, index: Int): Double {
    val lastIndex = (spec.blobCount - 1).coerceAtLeast(0)
    val steps = lastIndex.coerceAtLeast(1)
    val ratio = index.coerceIn(0, lastIndex).toDouble() / steps
    val lower = spec.radiusRange.start
    return lower + (spec.radiusRange.endInclusive - lower) * ratio
}

/**
 * 第 [index] 个光斑的透明度。
 *
 * 保证落在 `(0, maxAlpha]` 内：三档系数**最大就是 1.0**，所以运行时透明度永远不会超过
 * [BackgroundSpec.maxAlpha]，而 `maxAlpha` 又被单测钉在 ≤ 0.08（计划 §3 / 风险 R-1）。
 */
fun backgroundBlobAlpha(spec: BackgroundSpec, index: Int): Double =
    spec.maxAlpha * ALPHA_STEPS[index.mod(ALPHA_STEPS.size)]

/**
 * 背景光斑的可用颜色（计划 §3 最后一条 / 风险 R-1）。
 *
 * **只返回中性色角色**：`primary` 承载「选中」、`tertiary` 承载「提示」、`error` 承载「错误」，
 * 背景若从这三个强调色取色，会直接削弱棋盘上这三个信号的辨识度 —— 这是本版本最容易犯的错，
 * 因此这里连「可以传强调色」的口子都不留（颜色只能经本函数取得）。
 *
 * 三个中性角色混用是为了让光斑之间略有色相层次（同一角色只有亮度差异，叠在一起会像一块脏斑）。
 * 返回值**恒非空**（调用方按 `index % size` 取用）；4 套主题色下都成立，因为颜色由
 * 当前 [ColorScheme] 推导而非写死。
 */
fun backgroundBlobColors(colors: ColorScheme): List<Color> = listOf(
    colors.surfaceVariant,
    colors.outlineVariant,
    colors.onSurfaceVariant,
)

/**
 * 应用背景：静态竖向渐变 + 缓慢漂移的柔和光斑（V1.5 / SRS FR-13.9；原有渐变见 FR-13.8）。
 *
 * 由 `MainActivity` 铺满全屏地放在所有导航内容**之下**（Box 的子级按声明顺序绘制，
 * 因此它必须是第一个子级）。背景不参与测量与布局（计划 §5），只是叠在最底层。
 *
 * @param modifier 由调用方决定铺多大；未指定时也会铺满父容器。
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    // 生命周期门控（计划 §5 第一条）：只有前台 RESUMED 才组合出「会动」的那一层。
    // 非前台的实现方式是**把动画所在的组合整段移除**，于是 rememberInfiniteTransition 的
    // 协程随节点一同被取消 —— 不是「画一帧同位置」那种假停，而是真的不再请求任何帧。
    // 详见 [rememberIsResumed] 与 [DriftingBlobs] 的注释。
    if (rememberIsResumed()) {
        DriftingBlobs(modifier = modifier)
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
 * 会动的那一层：基底渐变 + 若干缓慢漂移的径向渐变光斑。
 *
 * ### 动画状态在哪里读（计划 §4 第 1 条 / 风险 R-5）
 * 三个进度值都是 `State<Float>`，**只在 [Canvas] 的 draw lambda 内被读取**，组合期一次都不读。
 * `Canvas` 底层就是 `drawBehind`：draw 阶段读快照状态只会让**绘制阶段**失效，
 * 不会触发重组；若读在组合期（例如在函数体里写 `val p = slow.value`），
 * 那么每帧都会重组整棵子树 —— 这是本项目首次引入常驻动画，做错代价很大。
 *
 * ### 每帧的分配（计划 §4 第 3 条 / 风险 R-6）
 * 半径、颜色、透明度都不随帧变化，只有**位置**在变；位置改由画布平移承担，于是每个光斑的
 * `Brush` 可以整帧复用（见 [BlobBrushCache]）。稳态下每帧不新建任何 `Brush`；
 * 轨迹计算全程用 `Double` 标量，`Offset` / `Color` 是 JVM 值类，不产生堆分配。
 *
 * ### 为什么用正弦/余弦
 * 进度是 0→1 的循环（`RepeatMode.Restart`），若直接用它插值位置，循环回到 0 时位置会**跳变**。
 * sin/cos 以 1 为周期，`progress = 1` 与 `progress = 0` 算出的位置完全相同，
 * 因此循环处天然连续、看不出接缝。相邻光斑用不同的相位步长错开，形成各自独立的椭圆轨迹。
 */
@Composable
private fun DriftingBlobs(modifier: Modifier) {
    val spec = DefaultBackgroundSpec

    // 基底仍然是 V1.4 的那层竖向渐变（计划 §4 第 1 条：光斑叠加在其上），
    // 因此在 4 套主题色下背景的基调与 V1.4 保持一致。
    val background = appBackgroundBrush()
    // 颜色只能取自中性色角色（见 backgroundBlobColors 的说明）。
    val blobColors = backgroundBlobColors(MaterialTheme.colorScheme)

    // 常驻动画的时钟：**一个** transition 驱动三个不同速度的进度。
    // 计划 §4 第 2 条要求参数集中 —— 若每个光斑各配一个 transition，
    // Compose 就要维护 N 套动画状态与帧回调，纯属浪费（本项目的常驻动画应是最轻的那个）。
    val transition = rememberInfiniteTransition(label = "背景漂移")
    val periods = backgroundDriftPeriods(spec)
    val driftSlow = transition.driftProgress(periods[0], label = "漂移-慢")
    val driftMedium = transition.driftProgress(periods[1], label = "漂移-中")
    val driftFast = transition.driftProgress(periods[2], label = "漂移-快")

    // 画笔缓存：跨帧复用（风险 R-6）。用 remember 持有 —— 它跨组合存活，
    // 只在尺寸/配色变化时重建，与动画进度无关。
    val brushCache = remember { BlobBrushCache() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 零尺寸时半径无效（径向渐变不允许半径 ≤ 0），此时也没有任何东西可画。
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        drawRect(brush = background)

        val shortSide = min(size.width, size.height)
        val brushes = brushCache.brushesFor(size, spec, blobColors)

        for (index in 0 until spec.blobCount) {
            // ★ 动画状态在这里读：draw 阶段（见函数头注释与计划 §4 第 1 条 / 风险 R-5）。
            val progress = when (index % periods.size) {
                0 -> driftSlow.value
                1 -> driftMedium.value
                else -> driftFast.value
            }

            val radius = shortSide * backgroundBlobRadius(spec, index).toFloat()

            // 轨迹：以进度为参数的正弦/余弦。相位按光斑序号错开，
            // 于是每个光斑沿自己的一条椭圆缓慢漂移，互不同步。
            val centerX =
                size.width * (0.5 + WANDER_X * sin(TWO_PI * (progress + index * PHASE_X_STEP)))
            val centerY =
                size.height * (0.5 + WANDER_Y * cos(TWO_PI * (progress + index * PHASE_Y_STEP)))

            // 平移画布而不改变画笔：画笔的圆心恒在原点、半径固定，因此可以整帧复用。
            withTransform({ translate(left = centerX.toFloat(), top = centerY.toFloat()) }) {
                drawCircle(brush = brushes[index], radius = radius, center = Offset.Zero)
            }
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
 * 因此回到前台时会从相位 0 重新开始 —— 也就是说光斑位置会跳变一次。
 * 这是一处**有意识的取舍**：透明度只有 0.055 上下、运动极慢，切回来时看不出位置差；
 * 若要消除这次跳变，需要额外记录一个相位基准再加回去（约十行），已列入 V-5 的真机观察项。
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
 * 用 `LinearEasing` 让进度匀速推进：漂移的缓急交给 sin/cos 天然承担，
 * 再叠一层缓动只会让速度忽快忽慢、反而更引人注意。
 */
@Composable
private fun InfiniteTransition.driftProgress(durationMillis: Int, label: String): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = label,
    )

/**
 * 光斑画笔缓存（计划 §4 第 3 条 / 风险 R-6）。
 *
 * 光斑的半径、颜色、透明度都只取决于尺寸与配色，**不随帧变化**；变化的位置由画布平移承担。
 * 因此每个光斑的 `Brush` 只需在「尺寸或配色变化时」重建一次，稳态下每帧零分配 ——
 * 否则每帧新建 N 个径向渐变画笔会持续制造垃圾，在消除动画期间叠加成卡顿（风险 R-4）。
 *
 * 尺寸变化（旋转、分屏、首次绘制）才会重建，属于低频事件，重建代价可忽略。
 */
private class BlobBrushCache {

    private var cachedSize: Size = Size.Unspecified
    private var cachedColors: List<Color> = emptyList()
    private var cachedBlobCount: Int = -1
    private var cachedRadiusRange: ClosedRange<Double> = 0.0..0.0
    private var brushes: List<Brush> = emptyList()

    fun brushesFor(size: Size, spec: BackgroundSpec, blobColors: List<Color>): List<Brush> {
        val reusable = size == cachedSize &&
            blobColors == cachedColors &&
            spec.blobCount == cachedBlobCount &&
            spec.radiusRange == cachedRadiusRange
        if (reusable) return brushes

        cachedSize = size
        cachedColors = blobColors
        cachedBlobCount = spec.blobCount
        cachedRadiusRange = spec.radiusRange

        val shortSide = min(size.width, size.height)
        brushes = List(spec.blobCount) { index ->
            val radius = shortSide * backgroundBlobRadius(spec, index).toFloat()
            val alpha = backgroundBlobAlpha(spec, index).toFloat()
            // 同一光斑内三档混色，让相邻光斑不至于像同一块斑（见 backgroundBlobColors）。
            val color = blobColors[index % blobColors.size]
            Brush.radialGradient(
                // 圆心在原点、半径固定 —— 位置由调用方的画布平移决定。
                // 中间档略高于线性（近似 (1−r²)² 的柔和衰减）：边缘更快收敛到 0，
                // 光斑不会出现可辨的圆形硬边（风险 R-2：不得像任何具象形状）。
                // 最外圈用同色零透明而不是 Color.Transparent（后者是「透明黑」，
                // 渐变插值会把边缘染灰）。
                *arrayOf(
                    0.0f to color.copy(alpha = alpha),
                    0.5f to color.copy(alpha = alpha * 0.55f),
                    1.0f to color.copy(alpha = 0f),
                ),
                center = Offset.Zero,
                radius = radius,
            )
        }
        return brushes
    }
}
