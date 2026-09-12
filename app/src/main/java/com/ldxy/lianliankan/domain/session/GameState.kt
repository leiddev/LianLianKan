package com.ldxy.lianliankan.domain.session

import com.ldxy.lianliankan.domain.config.LevelConfig
import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.GamePhase
import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.model.TileState
import com.ldxy.lianliankan.domain.score.ScoreState

/**
 * 结算数据（SRS FR-10.4）。
 *
 * 通关与失败共用同一结构：失败时剩余时间为 0，时间奖励自然为 0，因此
 * [score] 在两处都等于「消除得分 + 时间奖励」（SRS FR-8.4）。
 */
data class GameResult(
    val isWin: Boolean,
    /** 含时间奖励的最终得分（SRS 9.2）。 */
    val score: Int,
    /** 本局最高连击（SRS FR-10.4）。 */
    val maxCombo: Int,
    /** 时间奖励部分（SRS FR-8.4）。 */
    val timeBonus: Int,
    val timeLeftSeconds: Int,
)

/**
 * 一次游戏会话的完整状态快照（对应 SRS 7.1 的 `GameState`）。不可变。
 *
 * 选中态与提示高亮按 SRS 7.1 的规定存放在 `Tile.state` 中
 * （`NORMAL / SELECTED / HINTED`），因此本类不另设 `selected` 字段，
 * 由 [selectedPosition] 派生，避免两处状态不同步。
 */
data class GameState(
    val config: LevelConfig,
    val board: Board,
    val phase: GamePhase,
    val score: ScoreState,
    /** 本关剩余提示次数（SRS FR-9.1）。 */
    val hintLeft: Int,
    /** 本关剩余洗牌次数（SRS FR-9.2）。 */
    val shuffleLeft: Int,
    val timeLeftSeconds: Int,
    /** 剩余时间是否低于 30 秒（SRS FR-7.4），供 UI 变色提示。 */
    val isTimeLow: Boolean,
    /** 通关或失败后的结算数据；进行中为 `null`（SRS FR-10.4）。 */
    val result: GameResult? = null,
) {
    /** 当前被选中的牌的位置；无选中时为 `null`（SRS FR-4.1）。 */
    val selectedPosition: Position?
        get() = board.remainingTiles()
            .firstOrNull { it.state == TileState.SELECTED }
            ?.position

    /** 被提示道具高亮的两张牌的位置（SRS FR-9.1）。 */
    val hintedPositions: List<Position>
        get() = board.remainingTiles()
            .filter { it.state == TileState.HINTED }
            .map { it.position }

    val remainingTiles: Int get() = board.remainingCount

    val isPlaying: Boolean get() = phase == GamePhase.PLAYING

    val isFinished: Boolean get() = phase == GamePhase.WIN || phase == GamePhase.LOSE
}
