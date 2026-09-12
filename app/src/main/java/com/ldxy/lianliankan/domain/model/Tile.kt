package com.ldxy.lianliankan.domain.model

/** 牌的状态（SRS 7.1）。 */
enum class TileState {
    NORMAL,

    /** 已被玩家选中（SRS FR-4.1）。 */
    SELECTED,

    /** 被提示道具高亮（SRS FR-9.1）。 */
    HINTED,
}

/**
 * 棋盘上的一张牌（SRS 7.1）。
 *
 * [row] / [col] 与 [Board] 中的存放位置始终一致：构造棋盘时按坐标落位，
 * 移动（洗牌）时用 [movedTo] 同步更新，因此不存在两者不一致的中间态。
 */
data class Tile(
    /** 唯一标识，同一棋盘内不重复（SRS 7.1）。 */
    val id: Int,
    /** 图案类型，取值 `0 until typeCount`；同类型可配对（SRS 7.1）。 */
    val type: Int,
    val row: Int,
    val col: Int,
    val state: TileState = TileState.NORMAL,
) {
    val position: Position get() = Position(row, col)

    /** 洗牌时把牌移动到新坐标（SRS FR-3.4 / UC-05）。 */
    fun movedTo(row: Int, col: Int): Tile = copy(row = row, col = col)

    fun withState(state: TileState): Tile = copy(state = state)
}
