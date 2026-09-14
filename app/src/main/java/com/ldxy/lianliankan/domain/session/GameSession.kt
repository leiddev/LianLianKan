package com.ldxy.lianliankan.domain.session

import com.ldxy.lianliankan.domain.board.BoardFactory
import com.ldxy.lianliankan.domain.board.BoardGenerator
import com.ldxy.lianliankan.domain.board.DeadlockDetector
import com.ldxy.lianliankan.domain.board.ShuffleService
import com.ldxy.lianliankan.domain.config.LevelConfig
import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.GamePhase
import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.model.TileState
import com.ldxy.lianliankan.domain.path.PathFinder
import com.ldxy.lianliankan.domain.score.GameTimer
import com.ldxy.lianliankan.domain.score.ScoreEngine
import com.ldxy.lianliankan.domain.score.ScoreState

/**
 * 游戏会话聚合根：把棋盘生成、连通判定、死局洗牌、计分连击与倒计时组装成一局游戏
 * （开发计划 M5，SRS UC-03 / 3.4 状态流程）。
 *
 * 纯 Kotlin，不依赖 Android；时间源注入，因此可在 JVM 上完整跑完一局并断言结果（SRS NFR-3.1）。
 *
 * ### 状态机
 * 与 SRS 3.4 一致：`READY →（start）→ PLAYING ⇄（pause/resume）→ PAUSED`，
 * `PLAYING →（清盘）→ WIN`，`PLAYING →（时间耗尽）→ LOSE`。
 * 重开与重试都回到 `PLAYING`；是否保留已解锁关卡由外部进度（M8）决定，本类不涉及（FR-10.7）。
 *
 * ### 一次消除的事务顺序
 * 严格按 SRS UC-03 主流程：校验选中 → 判连通 → 移除两张牌 → 计分与连击 →
 * 死局检测（必要时自动洗牌，不消耗道具次数）→ 判定通关。
 */
class GameSession(
    val config: LevelConfig,
    private val boardFactory: BoardFactory = BoardGenerator(),
    private val shuffleService: ShuffleService = ShuffleService(),
    private val clock: () -> Long = GameTimer.MONOTONIC_CLOCK,
) {

    private val timer = GameTimer(config.timeLimitSeconds, clock)

    private var current: GameState = GameState(
        config = config,
        board = Board.empty(config.rows, config.cols),
        phase = GamePhase.READY,
        score = ScoreState(),
        hintLeft = config.hintCount,
        shuffleLeft = config.shuffleCount,
        timeLeftSeconds = config.timeLimitSeconds,
        isTimeLow = false,
    )

    /** 当前状态快照。每次操作后都会更新。 */
    val state: GameState get() = current

    // ================================================================ 开局

    /**
     * 开始（或重开 / 重试）本关：重新生成棋盘，重置得分、道具次数与倒计时。
     *
     * SRS FR-9.5 要求每次开局（含重试）都把道具次数恢复为配置值；SRS FR-10.7 的
     * 「重开不重置已解锁关卡」由外部进度负责。
     */
    fun start(): StartResult {
        var board = boardFactory.generate(config)
        var autoShuffled = false

        // 开发计划 9.2 节 P-2：随机填充只保证配对完整，不保证开局存在可连通的一对
        if (!DeadlockDetector.hasMove(board)) {
            board = shuffleService.shuffle(board)
            autoShuffled = true
        }

        timer.start()
        current = GameState(
            config = config,
            board = board,
            phase = GamePhase.PLAYING,
            score = ScoreState(),
            hintLeft = config.hintCount,
            shuffleLeft = config.shuffleCount,
            timeLeftSeconds = timer.remainingSeconds(),
            isTimeLow = timer.isTimeLow,
        )
        return StartResult(state = current, autoShuffled = autoShuffled)
    }

    /** 重开本关（SRS FR-10.1 的「重开本关」与 FR-10.6 的「重试本关」）。 */
    fun restart(): StartResult = start()

    // ================================================================ 选中与消除

    /**
     * 点击棋盘上的一张牌（SRS FR-4、UC-03 主流程）。
     */
    fun select(position: Position): SelectResult {
        val snapshot = current
        if (snapshot.phase != GamePhase.PLAYING) return SelectResult.Ignored

        // 玩家一旦操作，就撤掉提示高亮
        var board = clearHighlight(snapshot.board)

        val tile = board.tileAt(position) ?: return SelectResult.Ignored

        val selectedPosition = board.remainingTiles()
            .firstOrNull { it.state == TileState.SELECTED }
            ?.position

        // 尚无选中 → 选中该牌
        if (selectedPosition == null) {
            current = snapshot.copy(board = board.withTile(tile.withState(TileState.SELECTED)))
            return SelectResult.Selected
        }

        // 点击的是已选中的牌 → 取消选中
        if (selectedPosition == position) {
            current = snapshot.copy(board = board.withTile(tile.withState(TileState.NORMAL)))
            return SelectResult.Deselected
        }

        val firstTile = board.tileAt(selectedPosition)
            ?: return SelectResult.Ignored

        // 图案不同 → 错误操作（FR-4.5）
        if (firstTile.type != tile.type) {
            // 与「同图案但连不通」保持一致：出错即清空选中态，两张都不再选中。
            // V1.0 及更早的版本在这里是把选中态转移给第二张（SRS FR-4.5 原文），
            // V1.1 按验收意见改为清空（见 doc/V1.1改进计划.md 的 I-1）。
            current = snapshot.copy(
                board = clearSelection(board),
                score = resetCombo(snapshot.score),
            )
            return SelectResult.WrongType(first = selectedPosition, second = position)
        }

        // 图案相同但不可连通 → 不消除，并清空选中态（FR-4.4）
        val path = PathFinder.find(board, selectedPosition, position)
        if (path == null) {
            // 两张牌都不再选中：出错后回到「未选」状态，下一次点击从干净状态开始。
            // V1.0 的行为是保留第一张选中（决策 D-4，便于直接换搭档），
            // V1.1 按验收意见撤销 D-4 改为完全清空（见 doc/V1.1改进计划.md 的 I-1）。
            current = snapshot.copy(
                board = clearSelection(board),
                score = resetCombo(snapshot.score),
            )
            return SelectResult.NoPath(first = selectedPosition, second = position)
        }

        return eliminate(snapshot, board, firstTile.position, position, path)
    }

    private fun eliminate(
        snapshot: GameState,
        board: Board,
        first: Position,
        second: Position,
        path: com.ldxy.lianliankan.domain.path.PathResult,
    ): SelectResult {
        val firstTile = board.tileAt(first)!!
        val secondTile = board.tileAt(second)!!

        val score = ScoreEngine.onEliminated(snapshot.score, clock())
        val gained = score.points - snapshot.score.points

        var cleared = board
            .withTile(firstTile.withState(TileState.NORMAL))
            .withTile(secondTile.withState(TileState.NORMAL))
            .without(first.row, first.col)
            .without(second.row, second.col)

        // 通关判定（SRS FR-10.2）
        if (cleared.isCleared) {
            val next = refreshTime(
                snapshot.copy(board = cleared, score = score, phase = GamePhase.WIN),
            )
            current = next.copy(result = buildResult(next, isWin = true))
            return SelectResult.Eliminated(
                first = firstTile,
                second = secondTile,
                path = path,
                gainedPoints = gained,
                combo = score.combo,
                autoShuffled = false,
            )
        }

        // 死局检测与自动洗牌（SRS FR-6.1 / FR-6.2），不消耗玩家洗牌次数
        var autoShuffled = false
        if (!DeadlockDetector.hasMove(cleared)) {
            cleared = shuffleService.shuffle(cleared)
            autoShuffled = true
        }

        current = refreshTime(snapshot.copy(board = cleared, score = score))
        return SelectResult.Eliminated(
            first = firstTile,
            second = secondTile,
            path = path,
            gainedPoints = gained,
            combo = score.combo,
            autoShuffled = autoShuffled,
        )
    }

    // ================================================================ 道具

    /**
     * 使用提示道具：高亮一对当前可连通的牌（SRS FR-9.1）。
     *
     * SRS FR-9.4 明确要求**使用提示不重置连击**，因此这里不动 [ScoreState]。
     */
    fun useHint(): HintResult {
        val snapshot = current
        if (snapshot.phase != GamePhase.PLAYING) return HintResult.NotAvailable
        if (snapshot.hintLeft <= 0) return HintResult.NoHintLeft

        val move = DeadlockDetector.findMove(snapshot.board) ?: return HintResult.NotAvailable

        val board = clearHighlight(snapshot.board)
            .withTile(move.first.withState(TileState.HINTED))
            .withTile(move.second.withState(TileState.HINTED))

        current = snapshot.copy(board = board, hintLeft = snapshot.hintLeft - 1)
        return HintResult.Hinted(
            first = move.first.position,
            second = move.second.position,
            remaining = current.hintLeft,
        )
    }

    /**
     * 使用洗牌道具：重排剩余牌（SRS FR-9.2）。
     *
     * 按 SRS FR-8.3 / FR-9.4：清空选中态（UC-05 步骤 4）并把连击归零。
     */
    fun useShuffle(): ShuffleResult {
        val snapshot = current
        if (snapshot.phase != GamePhase.PLAYING) return ShuffleResult.NotAvailable
        if (snapshot.shuffleLeft <= 0) return ShuffleResult.NoShuffleLeft

        val board = clearHighlight(snapshot.board).let { clearSelection(it) }

        current = snapshot.copy(
            board = shuffleService.shuffle(board),
            shuffleLeft = snapshot.shuffleLeft - 1,
            score = ScoreEngine.resetCombo(snapshot.score),
        )
        return ShuffleResult.Shuffled(remaining = current.shuffleLeft)
    }

    // ================================================================ 暂停 / 继续 / 心跳

    /** 暂停：倒计时停止（SRS FR-7.3、FR-10.1）。 */
    fun pause() {
        val snapshot = current
        if (snapshot.phase != GamePhase.PLAYING) return
        timer.pause()
        current = snapshot.copy(phase = GamePhase.PAUSED)
    }

    /** 继续：倒计时恢复（SRS FR-7.3）。 */
    fun resume() {
        val snapshot = current
        if (snapshot.phase != GamePhase.PAUSED) return
        timer.resume()
        current = refreshTime(snapshot.copy(phase = GamePhase.PLAYING))
    }

    /**
     * 心跳：刷新剩余时间，并在时间耗尽时判定失败（SRS FR-7.1 / FR-7.2）。
     *
     * 由 `GameViewModel` 周期性调用；暂停或已结束时是空操作。
     */
    fun tick(): GameState {
        val snapshot = current
        if (snapshot.phase != GamePhase.PLAYING) return snapshot

        val refreshed = refreshTime(snapshot)
        current = if (timer.isExpired) {
            refreshed.copy(
                phase = GamePhase.LOSE,
                result = buildResult(refreshed, isWin = false),
            )
        } else {
            refreshed
        }
        return current
    }

    /** 倒计时剩余毫秒数，供 UI 做更平滑的进度环（SRS FR-7.1）。 */
    fun remainingMillis(): Long = timer.remainingMillis()

    val timerLimitMillis: Long get() = timer.limitMillis

    // ================================================================ 内部工具

    private fun refreshTime(state: GameState): GameState = state.copy(
        timeLeftSeconds = timer.remainingSeconds(),
        isTimeLow = timer.isTimeLow,
    )

    private fun buildResult(state: GameState, isWin: Boolean): GameResult {
        val timeLeft = state.timeLeftSeconds
        return GameResult(
            isWin = isWin,
            score = ScoreEngine.finalScore(state.score, timeLeft),
            maxCombo = state.score.maxCombo,
            timeBonus = ScoreEngine.timeBonus(timeLeft),
            timeLeftSeconds = timeLeft,
        )
    }

    /** SRS FR-8.3：错误选中导致连击归零。 */
    private fun resetCombo(score: ScoreState): ScoreState = ScoreEngine.resetCombo(score)

    private fun clearSelection(board: Board): Board =
        board.remainingTiles()
            .filter { it.state == TileState.SELECTED }
            .fold(board) { acc, tile -> acc.withTile(tile.withState(TileState.NORMAL)) }

    /** 清除提示高亮；选中态不受影响。 */
    private fun clearHighlight(board: Board): Board =
        board.remainingTiles()
            .filter { it.state == TileState.HINTED }
            .fold(board) { acc, tile -> acc.withTile(tile.withState(TileState.NORMAL)) }
}
