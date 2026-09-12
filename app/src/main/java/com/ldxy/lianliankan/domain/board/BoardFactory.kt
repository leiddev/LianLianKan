package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.config.LevelConfig
import com.ldxy.lianliankan.domain.model.Board

/**
 * 棋盘生成的抽象。
 *
 * 生产代码使用 [BoardGenerator]；这层接口的价值在于让 `GameSession` 的测试可以注入
 * **手工构造的固定棋盘**，从而验证「消除后恰好进入死局 → 自动洗牌」这类难以靠随机生成
 * 复现的路径（SRS FR-6.2）。
 */
fun interface BoardFactory {
    fun generate(config: LevelConfig): Board
}
