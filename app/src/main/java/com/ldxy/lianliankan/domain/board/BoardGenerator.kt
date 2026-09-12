package com.ldxy.lianliankan.domain.board

import com.ldxy.lianliankan.domain.config.LevelConfig
import com.ldxy.lianliankan.domain.model.Board
import com.ldxy.lianliankan.domain.model.Tile
import kotlin.random.Random

/**
 * 棋盘生成器（SRS FR-3.2 / FR-3.3）。
 *
 * 流程：按 [TileTypeDistributor] 构造「成对图案集合」→ 随机打乱 → 按行优先填入棋盘。
 * 这保证了 FR-3.1 的前提（牌总数为偶数、每类图案张数为偶数），
 * 因而牌**总能被成对消完**。
 *
 * 注意：随机填充不保证开局就存在可连通的一对（SRS FR-3.2 的「保证理论可解」只覆盖
 * 配对完整性）。开局死局由 M3 的 `ShuffleService` 与 M5 的会话逻辑处理，
 * 见开发计划 9.2 节 P-2。
 *
 * [random] 可注入，便于测试复现（SRS NFR-2.1）。
 */
class BoardGenerator(
    private val random: Random = Random.Default,
) {

    /** 按配置生成一副新棋盘。 */
    fun generate(config: LevelConfig): Board {
        val bag = typeBag(config.tileCount, config.typeCount).shuffled(random)

        val tiles = ArrayList<Tile>(bag.size)
        var id = 0
        for (row in 0 until config.rows) {
            for (col in 0 until config.cols) {
                tiles += Tile(
                    id = id++,
                    type = bag[row * config.cols + col],
                    row = row,
                    col = col,
                )
            }
        }
        return Board.of(config.rows, config.cols, tiles)
    }

    /** 展开成「每类图案各若干张」的一维集合，长度等于牌总数。 */
    private fun typeBag(tileCount: Int, typeCount: Int): List<Int> =
        TileTypeDistributor.countsFor(tileCount, typeCount)
            .flatMapIndexed { type, count -> List(count) { type } }
}
