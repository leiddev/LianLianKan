package com.ldxy.lianliankan.domain.model

/**
 * 游戏阶段（SRS 7.1）。
 *
 * 取值与 SRS 3.4 的状态流程一一对应：
 * READY=就绪 / PLAYING=游戏进行 / PAUSED=暂停 / WIN=通关结算 / LOSE=失败结算。
 */
enum class GamePhase {
    READY,
    PLAYING,
    PAUSED,
    WIN,
    LOSE,
}
