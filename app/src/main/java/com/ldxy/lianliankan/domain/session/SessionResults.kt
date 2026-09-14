package com.ldxy.lianliankan.domain.session

import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.model.Tile
import com.ldxy.lianliankan.domain.path.PathResult

/**
 * `select` 的结果。UI 依此决定播放哪种反馈（SRS SRS 4.x / 5.1 的 Snackbar 提示）。
 */
sealed interface SelectResult {

    /** 点击被忽略：阶段不是「游戏进行」，或该位置没有牌（含越界）。 */
    data object Ignored : SelectResult

    /** 选中第一张牌（SRS FR-4.1）。 */
    data object Selected : SelectResult

    /** 再次点击同一张牌，取消选中（SRS FR-4.2）。 */
    data object Deselected : SelectResult

    /**
     * 两张牌图案不同（SRS FR-4.5）。
     *
     * 视为错误操作：给出错误反馈，并把**第二张**牌设为选中，连击归零（SRS FR-8.3）。
     */
    data class WrongType(val first: Position, val second: Position) : SelectResult

    /**
     * 两张牌图案相同但不可连通（SRS FR-4.4）。
     *
     * 不消除，给出「无法连通」提示；连击归零（SRS FR-8.3），
     * 并**清空选中态** —— 出错后回到「未选」状态，下一次点击从干净状态开始
     * （V1.1 改进 I-1，撤销了 V1.0 的决策 D-4）。
     */
    data class NoPath(val first: Position, val second: Position) : SelectResult

    /**
     * 消除成功（SRS FR-4.3 / FR-4.6）。
     *
     * @param path 供 UI 绘制连线与消除动画（SRS FR-5.6）。
     * @param gainedPoints 本次得分。
     * @param combo 本次消除后的连击数。
     * @param autoShuffled 本次消除后棋盘进入死局并已**自动**重排（SRS FR-6.2），
     *   UI 应提示「已自动重排」；该重排**不消耗**洗牌道具次数。
     */
    data class Eliminated(
        val first: Tile,
        val second: Tile,
        val path: PathResult,
        val gainedPoints: Int,
        val combo: Int,
        val autoShuffled: Boolean,
    ) : SelectResult
}

/** `useHint` 的结果（SRS FR-9.1 / FR-9.3）。 */
sealed interface HintResult {

    /** 当前阶段不可用，或棋盘上已无可行步。 */
    data object NotAvailable : HintResult

    /** 本关提示次数已用尽，按钮应置灰（SRS FR-9.3）。 */
    data object NoHintLeft : HintResult

    /** 已高亮一对可连通的牌（SRS FR-9.1）。 */
    data class Hinted(
        val first: Position,
        val second: Position,
        val remaining: Int,
    ) : HintResult
}

/** `useShuffle` 的结果（SRS FR-9.2 / FR-9.3）。 */
sealed interface ShuffleResult {

    /** 当前阶段不可用。 */
    data object NotAvailable : ShuffleResult

    /** 本关洗牌次数已用尽，按钮应置灰（SRS FR-9.3）。 */
    data object NoShuffleLeft : ShuffleResult

    /** 已重排，剩余次数为 [remaining]。 */
    data class Shuffled(val remaining: Int) : ShuffleResult
}

/**
 * 开局（含重开 / 重试）的结果。
 *
 * @param autoShuffled 初始棋盘的随机填充不保证开局有解（开发计划 9.2 节 P-2），
 *   若生成结果恰为死局会自动重排；此时为 `true`。
 */
data class StartResult(
    val state: GameState,
    val autoShuffled: Boolean,
)
