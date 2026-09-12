package com.ldxy.lianliankan.domain.model

/**
 * 棋盘坐标（SRS 7.1）。
 *
 * 刻意使用 `Int` 而非无符号类型：SRS FR-3.5 / 9.1 要求把棋盘四周视为可通行的虚拟空白区，
 * 因此连通判定会产生 `-1` 或 `== rows/cols` 这类越界坐标，需要能正常表示。
 */
data class Position(val row: Int, val col: Int)
