package com.ldxy.lianliankan.domain

import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.Position
import com.ldxy.lianliankan.domain.model.Tile

/**
 * 测试工具：用 ASCII 图直观描述棋盘布局。
 *
 * 每一行是一个字符串，`.` 表示空格，其余任意字符表示一张牌，字符本身即图案类型
 * （因此两处写同一个字符即为「同类型牌」）。例如：
 *
 * ```
 * val board = boardOf(
 *     "A.X",
 *     "X.X",
 *     "X.A",
 * )
 * ```
 *
 * 用字符而非数字，是为了让「谁和谁能配对」在测试里一眼可见。
 */
fun boardOf(vararg rows: String): Board {
    require(rows.isNotEmpty()) { "棋盘至少要有一行" }
    val cols = rows[0].length
    val tiles = mutableListOf<Tile>()
    var id = 0
    rows.forEachIndexed { row, line ->
        require(line.length == cols) { "第 $row 行长度为 ${line.length}，与首行 $cols 不一致" }
        line.forEachIndexed { col, ch ->
            if (ch != '.') {
                tiles += Tile(id = id++, type = ch.code, row = row, col = col)
            }
        }
    }
    return Board.of(rows.size, cols, tiles)
}

/** 简写坐标，让测试里的 `(row, col)` 更易读。 */
fun at(row: Int, col: Int): Position = Position(row, col)
