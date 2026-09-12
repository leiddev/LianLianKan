package com.ldxy.lianliankan.ui.game

import com.ldxy.lianliankan.domain.model.Tile
import com.ldxy.lianliankan.domain.path.PathResult
import com.ldxy.lianliankan.domain.session.GameState

/**
 * 需要以一次性提示（Snackbar）形式告知玩家的消息（SRS 5.1）。
 *
 * 领域层不产生文案，只给出语义；M6/M7 负责映射到字符串资源。
 */
enum class GameMessage {
    /** 两张牌图案不同（SRS FR-4.5）。 */
    WRONG_TYPE,

    /** 图案相同但不可连通（SRS FR-4.4）。 */
    NO_PATH,

    /** 提示次数已用尽（SRS FR-9.3）。 */
    NO_HINT_LEFT,

    /** 洗牌次数已用尽（SRS FR-9.3）。 */
    NO_SHUFFLE_LEFT,

    /** 死局后已自动重排（SRS FR-6.2）。 */
    AUTO_SHUFFLED,
}

/**
 * 由 ViewModel 发出、UI 消费一次即丢弃的效果。
 *
 * 与 [GameUiState] 分开走 `SharedFlow`，避免把「一次性的动画与提示」塞进持久状态导致
 * 重组时重复播放（Compose 中常见的一类 bug）。
 */
sealed interface GameEffect {

    /**
     * 一次成功消除。UI 依此绘制连线并播放消除动画（SRS FR-4.6 / FR-13.5 / FR-13.6）。
     *
     * [first] / [second] 是**已被移出棋盘**的两张牌，因此 UI 需要用它们的位置与图案
     * 自行渲染动画期间的残影。
     */
    data class Eliminated(
        val first: Tile,
        val second: Tile,
        val path: PathResult,
        val gainedPoints: Int,
        val combo: Int,
    ) : GameEffect

    data class ShowMessage(val message: GameMessage) : GameEffect
}

/**
 * 游戏界面的渲染状态（开发计划 M5-7）。
 *
 * 目前只是 [GameState] 的一层包装；M6 会在此追加仅与呈现有关的字段
 * （棋盘单元格尺寸、动画进行中标记等），保持状态与效果分离。
 */
data class GameUiState(
    val game: GameState,
)
