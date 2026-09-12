package com.ldxy.lianliankan.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldxy.lianliankan.domain.config.LevelConfig
import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.score.GameTimer
import com.ldxy.lianliankan.domain.session.GameSession
import com.ldxy.lianliankan.domain.session.HintResult
import com.ldxy.lianliankan.domain.session.SelectResult
import com.ldxy.lianliankan.domain.session.ShuffleResult
import com.ldxy.lianliankan.domain.session.StartResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 游戏界面的 ViewModel（SRS 2.5 的 MVVM 分层、开发计划 M5-7）。
 *
 * 职责只有三件：把 UI 事件转成 [GameSession] 调用、把会话状态推成 [StateFlow]、
 * 用 [viewModelScope] 周期性驱动倒计时。所有游戏规则都留在领域层，本类不含任何判断逻辑。
 *
 * 状态与一次性效果分成两条流：[uiState] 供 Compose 渲染，[effects] 供 Snackbar 与动画消费。
 *
 * @param clock 时间源，注入以便测试；默认单调时钟（SRS 5.2）。
 * @param tickIntervalMillis 倒计时刷新间隔。倒计时以秒为显示粒度，200ms 足以让跳秒不滞后。
 */
class GameViewModel(
    config: LevelConfig,
    clock: () -> Long = GameTimer.MONOTONIC_CLOCK,
    private val tickIntervalMillis: Long = DEFAULT_TICK_INTERVAL_MILLIS,
) : ViewModel() {

    private val session = GameSession(config = config, clock = clock)

    private val _uiState = MutableStateFlow(GameUiState(session.state))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = EFFECT_BUFFER_SIZE)
    val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    private var tickJob: Job? = null

    init {
        begin(session.start())
    }

    /** 开始本关；也用于「重开本关」与「重试本关」（SRS FR-10.1 / FR-10.6）。 */
    fun start() {
        begin(session.start())
    }

    fun onRestartClick() = start()

    /** 点击棋盘上的一张牌（SRS FR-4）。 */
    fun onTileClick(position: Position) {
        when (val result = session.select(position)) {
            is SelectResult.Eliminated -> {
                publish()
                _effects.tryEmit(
                    GameEffect.Eliminated(
                        first = result.first,
                        second = result.second,
                        path = result.path,
                        gainedPoints = result.gainedPoints,
                        combo = result.combo,
                    ),
                )
                if (result.autoShuffled) {
                    _effects.tryEmit(GameEffect.ShowMessage(GameMessage.AUTO_SHUFFLED))
                }
            }

            is SelectResult.WrongType -> {
                publish()
                _effects.tryEmit(
                    GameEffect.Rejected(
                        first = result.first,
                        second = result.second,
                        message = GameMessage.WRONG_TYPE,
                    ),
                )
            }

            is SelectResult.NoPath -> {
                publish()
                _effects.tryEmit(
                    GameEffect.Rejected(
                        first = result.first,
                        second = result.second,
                        message = GameMessage.NO_PATH,
                    ),
                )
            }

            SelectResult.Selected,
            SelectResult.Deselected,
            SelectResult.Ignored,
            -> publish()
        }
    }

    /** 使用提示道具（SRS FR-9.1）。 */
    fun onHintClick() {
        when (session.useHint()) {
            is HintResult.Hinted -> publish()
            HintResult.NoHintLeft ->
                _effects.tryEmit(GameEffect.ShowMessage(GameMessage.NO_HINT_LEFT))

            HintResult.NotAvailable -> Unit
        }
    }

    /** 使用洗牌道具（SRS FR-9.2）。 */
    fun onShuffleClick() {
        when (session.useShuffle()) {
            is ShuffleResult.Shuffled -> publish()
            ShuffleResult.NoShuffleLeft ->
                _effects.tryEmit(GameEffect.ShowMessage(GameMessage.NO_SHUFFLE_LEFT))

            ShuffleResult.NotAvailable -> Unit
        }
    }

    /** 暂停（SRS FR-10.1）。 */
    fun onPauseClick() {
        session.pause()
        publish()
    }

    /** 从暂停恢复（SRS FR-10.1）。 */
    fun onResumeClick() {
        session.resume()
        publish()
    }

    // ================================================================ 内部

    private fun begin(result: StartResult) {
        publish()
        if (result.autoShuffled) {
            _effects.tryEmit(GameEffect.ShowMessage(GameMessage.AUTO_SHUFFLED))
        }
        startTicking()
    }

    private fun publish() {
        _uiState.value = GameUiState(session.state)
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(tickIntervalMillis)
                val state = session.tick()
                publish()
                if (state.isFinished) break
            }
        }
    }

    companion object {
        const val DEFAULT_TICK_INTERVAL_MILLIS: Long = 200L

        private const val EFFECT_BUFFER_SIZE = 16

        /**
         * 为指定关卡构造 [GameViewModel] 的工厂。
         *
         * M7 接入导航后由 `GameScreen` 的路由参数提供 [LevelConfig]；
         * M6 阶段由 `MainActivity` 直接传入第 1 关，便于在没有导航的条件下验证棋盘交互。
         */
        fun factory(config: LevelConfig): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(config) }
        }
    }
}
