package com.ldxy.lianliankan.ui.game

import androidx.lifecycle.viewModelScope
import com.ldxy.lianliankan.domain.board.DeadlockDetector
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.GamePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `GameViewModel` 的事件流转与状态推送（开发计划 M5-7 的验证，M6 阶段补齐）。
 *
 * 时间源固定为常量、心跳间隔拉到极大，使倒计时不会干扰这些用例；游戏规则本身由
 * `GameSessionTest` 覆盖，这里只验证「UI 事件 → 会话 → StateFlow / SharedFlow」这条链路。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val config = LevelCatalog.firstLevel

    /** 供 tearDown 取消心跳循环，见 [tearDown]。 */
    private var viewModel: GameViewModel? = null

    /** 时间恒定，避免虚拟时间推进导致倒计时意外归零。 */
    private fun newViewModel(): GameViewModel = GameViewModel(
        config = config,
        clock = { 0L },
        tickIntervalMillis = 1_000_000L,
    ).also { viewModel = it }

    /**
     * Main 调度器刻意**不**绑定 `runTest` 的测试调度器。
     *
     * `GameViewModel` 的心跳是一个 `while (isActive) { delay(...) }` 的常驻循环；若它与
     * `runTest` 共用虚拟时间调度器，`runTest` 收尾时会为了「等到调度器空闲」而无限推进
     * 虚拟时间，表现为测试永久空转（不是卡死，而是烧 CPU）。
     *
     * 心跳放到真实调度器上，本类用例只断言「构造后的初始状态」与「事件调用后的同步状态推送」，
     * 二者都在调用线程上同步完成，因此不依赖心跳的时序。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        viewModel = null
        Dispatchers.resetMain()
    }

    @Test
    fun `初始化后直接开局并推送到 uiState`() = runTest {
        val viewModel = newViewModel()

        val state = viewModel.uiState.value.game
        assertEquals(GamePhase.PLAYING, state.phase)
        assertEquals(config.tileCount, state.remainingTiles)
        assertEquals(config.hintCount, state.hintLeft)
        assertEquals(config.shuffleCount, state.shuffleLeft)
    }

    @Test
    fun `点击一对可消除的牌会推送状态并发出 Eliminated 效果`() = runTest {
        val viewModel = newViewModel()
        val effects = mutableListOf<GameEffect>()
        val job = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        val move = DeadlockDetector.findMove(viewModel.uiState.value.game.board)!!
        viewModel.onTileClick(move.first.position)
        assertTrue(
            "首次点击应进入选中态",
            viewModel.uiState.value.game.selectedPosition != null,
        )

        viewModel.onTileClick(move.second.position)
        runCurrent()

        val state = viewModel.uiState.value.game
        assertEquals("应减少两张牌", config.tileCount - 2, state.remainingTiles)
        assertNull("消除后应清空选中态", state.selectedPosition)

        val eliminated = effects.filterIsInstance<GameEffect.Eliminated>()
        assertEquals("应恰好发出一次消除效果", 1, eliminated.size)
        assertEquals(move.first.position, eliminated.single().path.from)
        assertEquals(move.second.position, eliminated.single().path.to)
        assertTrue("首次消除得分为 10", eliminated.single().gainedPoints == 10)

        job.cancel()
    }

    @Test
    fun `选中同一张牌两次会取消选中`() = runTest {
        val viewModel = newViewModel()
        val position = DeadlockDetector.findMove(viewModel.uiState.value.game.board)!!.first.position

        viewModel.onTileClick(position)
        assertEquals(position, viewModel.uiState.value.game.selectedPosition)

        viewModel.onTileClick(position)
        assertNull(viewModel.uiState.value.game.selectedPosition)
    }

    @Test
    fun `暂停与继续会切换阶段`() = runTest {
        val viewModel = newViewModel()

        viewModel.onPauseClick()
        assertEquals(GamePhase.PAUSED, viewModel.uiState.value.game.phase)

        viewModel.onResumeClick()
        assertEquals(GamePhase.PLAYING, viewModel.uiState.value.game.phase)
    }

    @Test
    fun `提示用尽后发出提示耗尽的文案效果`() = runTest {
        val viewModel = newViewModel()
        val effects = mutableListOf<GameEffect>()
        val job = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        repeat(config.hintCount) { viewModel.onHintClick() }
        assertEquals(0, viewModel.uiState.value.game.hintLeft)
        assertEquals("最后一次提示仍应高亮两张牌", 2, viewModel.uiState.value.game.hintedPositions.size)

        viewModel.onHintClick()
        runCurrent()

        assertTrue(
            "次数用尽应发出提示文案效果",
            effects.filterIsInstance<GameEffect.ShowMessage>()
                .any { it.message == GameMessage.NO_HINT_LEFT },
        )

        job.cancel()
    }

    @Test
    fun `重开会重置得分与道具次数`() = runTest {
        val viewModel = newViewModel()
        viewModel.onHintClick()
        viewModel.onShuffleClick()
        assertTrue(viewModel.uiState.value.game.hintLeft < config.hintCount)

        viewModel.onRestartClick()

        val state = viewModel.uiState.value.game
        assertEquals(GamePhase.PLAYING, state.phase)
        assertEquals(config.hintCount, state.hintLeft)
        assertEquals(config.shuffleCount, state.shuffleLeft)
        assertEquals(0, state.score.points)
        assertEquals(config.tileCount, state.remainingTiles)
    }
}
