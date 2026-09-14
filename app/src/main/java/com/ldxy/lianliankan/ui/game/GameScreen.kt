package com.ldxy.lianliankan.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.R
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.GamePhase
import com.ldxy.lianliankan.feedback.FeedbackDispatcher
import com.ldxy.lianliankan.feedback.FeedbackEvent
import com.ldxy.lianliankan.ui.dialog.PauseDialog
import com.ldxy.lianliankan.ui.dialog.ResultDialog
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 游戏主界面（开发计划 M6）。
 *
 * 结构：顶部 HUD / 中部棋盘（叠一层连线画布）/ 底部操作条，外加一个 `SnackbarHost`。
 *
 * ### 动画编排（M6-5 / M6-6）
 * 一次消除拆成两段：先用约 [LINE_MILLIS]ms 沿路径把连线画出来，再让两张牌在
 * [FADE_MILLIS]ms 内缩放淡出。牌在领域层已经被移除，因此动画期间用 [GhostTile]
 * 在原位置渲染残影，避免「还没看清就消失」的突兀感。
 *
 * ### 坐标系
 * 中部的棋盘区域按 `(cols + 2) × (rows + 2)` 格分配空间，棋盘本体居中：外侧留出的一圈
 * 正好容纳绕棋盘外部的连线（SRS FR-3.5 / 9.1），与 [PathOverlay] 的坐标换算配套。
 * 代价是格子边长比「排满整个宽度」小一档，已在开发计划 9.2 节 P-3 记为已知取舍。
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    progressRepository: ProgressRepository,
    feedback: FeedbackDispatcher,
    onExitToMenu: () -> Unit,
    onNextLevel: (level: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.game

    // 文案在 composable 内解析；用 rememberUpdatedState 让长驻的 collector 始终读到最新值，
    // 又不会因为 map 每次重建而重启收集。
    val messages = mapOf(
        GameMessage.WRONG_TYPE to stringResource(R.string.msg_wrong_type),
        GameMessage.NO_PATH to stringResource(R.string.msg_no_path),
        GameMessage.NO_HINT_LEFT to stringResource(R.string.msg_no_hint_left),
        GameMessage.NO_SHUFFLE_LEFT to stringResource(R.string.msg_no_shuffle_left),
        GameMessage.AUTO_SHUFFLED to stringResource(R.string.msg_auto_shuffled),
    )
    val latestMessages by rememberUpdatedState(messages)

    var elimination by remember { mutableStateOf<GameEffect.Eliminated?>(null) }
    var rejection by remember { mutableStateOf<GameEffect.Rejected?>(null) }

    // 提示文案（V1.1 改进 I-2 / I-3）。
    // token 每次自增，作为下面 LaunchedEffect 的 key —— 这样**连点两次相同文案**
    // 也会重新计时；若只用文案做 key，第二条会继承第一条的剩余时间而提前消失。
    var notice by remember { mutableStateOf<Notice?>(null) }
    var noticeToken by remember { mutableIntStateOf(0) }
    val showNotice: (String) -> Unit = { text ->
        noticeToken += 1
        notice = Notice(text = text, token = noticeToken)
    }

    val lineProgress = remember { Animatable(0f) }
    val shakeProgress = remember { Animatable(0f) }

    // 提示到点自动消失。key 用 token：新提示一来就重启计时。
    LaunchedEffect(notice?.token) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }

    // 一次性效果：动画与提示（SRS FR-4.4 / FR-4.5 / FR-4.6 / FR-6.2 / FR-9.3）
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GameEffect.Eliminated -> {
                    feedback.dispatch(FeedbackEvent.ELIMINATE)
                    elimination = effect
                }

                is GameEffect.Selected -> feedback.dispatch(FeedbackEvent.SELECT)

                is GameEffect.Rejected -> {
                    feedback.dispatch(FeedbackEvent.ERROR)
                    rejection = effect
                    showNotice(latestMessages.getValue(effect.message))
                }

                is GameEffect.ShowMessage -> showNotice(latestMessages.getValue(effect.message))
            }
        }
    }

    // 通关 / 失败的音效与振动（SRS FR-12.1）。以阶段变化为触发点，
    // 避免与「消除」音效在同一次消除里重叠判断。
    LaunchedEffect(state.phase) {
        when (state.phase) {
            GamePhase.WIN -> feedback.dispatch(FeedbackEvent.WIN)
            GamePhase.LOSE -> feedback.dispatch(FeedbackEvent.LOSE)
            GamePhase.READY, GamePhase.PLAYING, GamePhase.PAUSED -> Unit
        }
    }

    // 连线生长，随后牌的缩放淡出
    LaunchedEffect(elimination) {
        if (elimination == null) return@LaunchedEffect
        lineProgress.snapTo(0f)
        lineProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LINE_MILLIS + FADE_MILLIS,
                easing = LinearEasing,
            ),
        )
        elimination = null
    }

    // 错误抖动
    LaunchedEffect(rejection) {
        if (rejection == null) return@LaunchedEffect
        shakeProgress.snapTo(0f)
        shakeProgress.animateTo(1f, tween(durationMillis = SHAKE_MILLIS, easing = LinearEasing))
        rejection = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 边到边后内容会绘制到状态栏下方，因此 HUD 与底部操作条各自避让系统栏
            // （V1.1 改进 I-4）。没有这层插边，顶部 HUD 会被状态栏压住。
            GameHud(
                state = state,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val cellSize = cellSizeFor(
                    availableWidth = maxWidth,
                    availableHeight = maxHeight,
                    cols = state.config.cols,
                    rows = state.config.rows,
                )
                val stageWidth = cellSize * (state.config.cols + 2)
                val stageHeight = cellSize * (state.config.rows + 2)

                val progress = lineProgress.value
                val fadeFraction = (
                    (progress * (LINE_MILLIS + FADE_MILLIS) - LINE_MILLIS) / FADE_MILLIS
                    ).coerceIn(0f, 1f)

                val ghosts = elimination
                    ?.takeIf { fadeFraction > 0f }
                    ?.let { current ->
                        val alpha = 1f - fadeFraction
                        val scale = 1f - GHOST_SHRINK * fadeFraction
                        listOf(
                            GhostTile(current.first, alpha, scale),
                            GhostTile(current.second, alpha, scale),
                        )
                    }
                    .orEmpty()

                val shakeOffset: Dp = if (rejection != null) {
                    (sin(shakeProgress.value * 3.0 * PI).toFloat() * SHAKE_AMPLITUDE_DP).dp
                } else {
                    0.dp
                }

                Box(
                    modifier = Modifier.size(width = stageWidth, height = stageHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    BoardView(
                        board = state.board,
                        cellSize = cellSize,
                        enabled = state.isPlaying,
                        onTileClick = viewModel::onTileClick,
                        rejectedPositions = rejection
                            ?.let { setOf(it.first, it.second) }
                            .orEmpty(),
                        shakeOffset = shakeOffset,
                        ghosts = ghosts,
                    )

                    // 连线画布与棋盘共用格子坐标，但整体外扩一圈以容纳绕外侧的路径
                    PathOverlay(
                        points = elimination?.path?.points.orEmpty(),
                        cellSize = cellSize,
                        progress = progress,
                        modifier = Modifier.size(width = stageWidth, height = stageHeight),
                    )
                }
            }

            GameActionBar(
                state = state,
                onHintClick = viewModel::onHintClick,
                onShuffleClick = viewModel::onShuffleClick,
                onPauseClick = viewModel::onPauseClick,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }

        // 提示文案（V1.1 改进 I-2 / I-3）。刻意**不用** Material 3 的 SnackbarHost：
        // 它内部按队列播放，后到的提示要等前一条显示完，与「后到即替换」的要求相反。
        // 这里直接渲染当前 notice，新提示一来旧的即刻消失、新的立刻出现。
        notice?.let { current ->
            NoticeBanner(
                text = current.text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 96.dp),
            )
        }
    }

    // 结算：通关时记录最佳分并解锁下一关（SRS FR-2.4 / FR-8.5 / FR-10.4）。
    // 放在这里是合适的——只有本屏幕同时知道「哪一关」与「什么结果」。
    val result = state.result
    var isNewRecord by remember { mutableStateOf(false) }
    LaunchedEffect(result) {
        isNewRecord = if (result != null && result.isWin) {
            progressRepository.recordCleared(state.config.level, result.score)
        } else {
            false
        }
    }

    when (state.phase) {
        // 暂停菜单（SRS FR-10.1）
        GamePhase.PAUSED -> PauseDialog(
            onResume = viewModel::onResumeClick,
            onRestart = viewModel::onRestartClick,
            onExit = onExitToMenu,
        )

        // 结算面板（SRS FR-10.2 – FR-10.6）
        GamePhase.WIN, GamePhase.LOSE -> result?.let {
            ResultDialog(
                result = it,
                hasNextLevel = state.config.level < LevelCatalog.levelCount,
                isNewRecord = isNewRecord,
                onNextLevel = { onNextLevel(state.config.level + 1) },
                onRetry = viewModel::onRestartClick,
                onExit = onExitToMenu,
            )
        }

        GamePhase.READY, GamePhase.PLAYING -> Unit
    }
}

/**
 * 计算格子边长（开发计划 M6-1）。
 *
 * 可用空间按 `cols + 2` / `rows + 2` 均分，多出的两格就是用于绕行连线的外圈。
 */
internal fun cellSizeFor(
    availableWidth: Dp,
    availableHeight: Dp,
    cols: Int,
    rows: Int,
): Dp {
    val byWidth = availableWidth.value / (cols + 2)
    val byHeight = availableHeight.value / (rows + 2)
    return min(byWidth, byHeight).coerceAtLeast(MIN_CELL_DP).dp
}

/** 连线生长动画时长（开发计划 M6-5）。 */
private const val LINE_MILLIS = 150

/** 消除动画（缩放 + 淡出）时长（开发计划 M6-6）。 */
private const val FADE_MILLIS = 250

/** 错误抖动时长（开发计划 M6-7）。 */
private const val SHAKE_MILLIS = 320

private const val SHAKE_AMPLITUDE_DP = 5f

/** 消除动画终态的缩放比例（1 - GHOST_SHRINK）。 */
private const val GHOST_SHRINK = 0.4f

/** 格子边长下限，避免极端窄屏下算出 0 导致布局异常。 */
private const val MIN_CELL_DP = 16f

/** 提示文案的显示时长（V1.1 改进 I-2）。比 Material 默认的 4 秒短 —— 它是操作反馈，不是需要阅读的通知。 */
private const val NOTICE_MILLIS = 1_800L

/**
 * 当前提示文案。
 *
 * [token] 每次自增，用于让 `LaunchedEffect` 在**文案相同**时也能重新计时（V1.1 改进 I-2）。
 */
private data class Notice(val text: String, val token: Int)

/**
 * 提示条（V1.1 改进 I-3）。
 *
 * 文字**居中**，背景为**浅色半透明**。浅色底在深浅两种主题下都保持同一表现
 * （见 doc/V1.1改进计划.md 的 I-3：若改为跟随主题，深色主题下就不是「浅色半透明」了），
 * 因此这里直接用固定的白色半透明 + 深色文字，而不取 `colorScheme`。
 *
 * 刻意不加淡入淡出动画：需求是「前一条立刻消失、立即显示最新的」，任何过渡都会削弱这一点。
 */
@Composable
private fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color.White.copy(alpha = 0.86f),
        contentColor = NOTICE_TEXT_COLOR,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** 浅色半透明底上的文字色：直接用近黑，保证在白色 86% 透明度下对比度充足。 */
private val NOTICE_TEXT_COLOR = Color(0xFF1B1B1B)
