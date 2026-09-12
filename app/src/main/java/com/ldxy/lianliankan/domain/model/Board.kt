package com.ldxy.lianliankan.domain.model

/**
 * 棋盘（SRS 7.1）。
 *
 * 不可变：所有「修改」方法都返回新的 [Board]，已经消除的格子以 `null` 表示。
 * 内部用一维列表按行优先存放，[rows] × [cols] 规模下比嵌套数组更省事，
 * 也让 [equals] / [hashCode] 具备值语义（便于测试断言与状态比对）。
 */
class Board private constructor(
    val rows: Int,
    val cols: Int,
    private val cells: List<Tile?>,
) {
    init {
        require(rows > 0 && cols > 0) { "棋盘尺寸必须为正：${rows}x$cols" }
        require(cells.size == rows * cols) { "格子数量与棋盘尺寸不符" }
    }

    /** 棋盘格总数（含已消除），即 SRS 附录 9.3 的「牌总数」。 */
    val tileCount: Int get() = rows * cols

    /** 仍未消除的牌数。 */
    val remainingCount: Int get() = cells.count { it != null }

    /** 是否已全部消除（触发通关，SRS FR-10.2）。 */
    val isCleared: Boolean get() = cells.all { it == null }

    fun contains(row: Int, col: Int): Boolean = row in 0 until rows && col in 0 until cols

    fun contains(position: Position): Boolean = contains(position.row, position.col)

    /** 取该格的牌；越界返回 `null`。 */
    fun tileAt(row: Int, col: Int): Tile? = if (contains(row, col)) cells[index(row, col)] else null

    fun tileAt(position: Position): Tile? = tileAt(position.row, position.col)

    /**
     * 该格能否作为连通路径经过（SRS BR-03 + FR-3.5）。
     *
     * 棋盘外侧视为永久空白，因此越界坐标一律返回 `true`；
     * 棋盘内则要求该格没有未消除的牌。
     */
    fun isPassable(row: Int, col: Int): Boolean =
        !contains(row, col) || cells[index(row, col)] == null

    fun isPassable(position: Position): Boolean = isPassable(position.row, position.col)

    /** 用同坐标的新牌替换原牌（用于切换选中态等）。 */
    fun withTile(tile: Tile): Board {
        require(contains(tile.row, tile.col)) {
            "牌 #${tile.id} 的坐标 (${tile.row}, ${tile.col}) 超出 ${rows}x$cols 棋盘"
        }
        if (cells[index(tile.row, tile.col)] == tile) return this
        val next = cells.toMutableList()
        next[index(tile.row, tile.col)] = tile
        return Board(rows, cols, next)
    }

    /** 消除该格的牌（SRS FR-4.3）。 */
    fun without(row: Int, col: Int): Board {
        require(contains(row, col)) { "坐标 ($row, $col) 超出 ${rows}x$cols 棋盘" }
        if (cells[index(row, col)] == null) return this
        val next = cells.toMutableList()
        next[index(row, col)] = null
        return Board(rows, cols, next)
    }

    /** 全部未消除的牌，按行优先顺序。 */
    fun remainingTiles(): List<Tile> = cells.filterNotNull()

    /** 按类型分组统计未消除牌的数量，用于洗牌保持分布与测试断言。 */
    fun remainingCountsByType(): Map<Int, Int> =
        remainingTiles().groupingBy { it.type }.eachCount()

    private fun index(row: Int, col: Int): Int = row * cols + col

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return rows == other.rows && cols == other.cols && cells == other.cells
    }

    override fun hashCode(): Int {
        var result = rows
        result = 31 * result + cols
        result = 31 * result + cells.hashCode()
        return result
    }

    override fun toString(): String =
        "Board(${cols}x$rows, remaining=$remainingCount)"

    companion object {
        /** 全部为空的棋盘。 */
        fun empty(rows: Int, cols: Int): Board {
            require(rows > 0 && cols > 0) { "棋盘尺寸必须为正：${rows}x$cols" }
            return Board(rows, cols, List(rows * cols) { null })
        }

        /**
         * 按牌的坐标落位构造棋盘，未提供牌的位置保持为空。
         *
         * 会校验坐标范围、id 唯一性与位置冲突，尽早暴露生成器或洗牌逻辑的缺陷。
         */
        fun of(rows: Int, cols: Int, tiles: List<Tile>): Board {
            require(rows > 0 && cols > 0) { "棋盘尺寸必须为正：${rows}x$cols" }
            val cells = arrayOfNulls<Tile>(rows * cols)
            val seenIds = HashSet<Int>(tiles.size)
            for (tile in tiles) {
                require(contains(rows, cols, tile.row, tile.col)) {
                    "牌 #${tile.id} 的坐标 (${tile.row}, ${tile.col}) 超出 ${rows}x$cols 棋盘"
                }
                require(seenIds.add(tile.id)) { "牌 id 重复：${tile.id}" }
                val i = tile.row * cols + tile.col
                require(cells[i] == null) {
                    "坐标 (${tile.row}, ${tile.col}) 上存在多张牌"
                }
                cells[i] = tile
            }
            return Board(rows, cols, cells.toList())
        }

        private fun contains(rows: Int, cols: Int, row: Int, col: Int): Boolean =
            row in 0 until rows && col in 0 until cols
    }
}
