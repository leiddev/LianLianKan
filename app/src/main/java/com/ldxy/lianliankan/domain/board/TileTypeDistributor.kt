package com.ldxy.lianliankan.domain.board

/**
 * 图案张数分配器 —— 实现开发计划 9.1 节「决策 D-1」。
 *
 * SRS FR-3.1 要求「每类图案数量均为偶数」，但附录 9.3 中有 6 个关卡的图案种类数
 * 不能整除牌总数（如第 2 关 48 张 / 9 类）。D-1 确认不改动 SRS 表格，改为补充分配规则：
 *
 * > `base` = 牌总数 ÷ 种类数 **向下取到最近的偶数**；
 * > 前 `k` 类各分得 `base + 2` 张，其余各类分得 `base` 张；
 * > `k` = (牌总数 − base × 种类数) ÷ 2。
 *
 * 由于 `base` 是「不超过商的最大偶数」，有 `商 − base < 2`，故 `k < 种类数` 恒成立；
 * 又因每类至少 `base ≥ 2` 张（由 [LevelConfig] 的前置校验保证），
 * 因此分配结果中每一类的张数都是正偶数，总和恰等于牌总数。
 */
object TileTypeDistributor {

    /**
     * 返回长度为 `typeCount` 的张数列表，第 `i` 项是类型 `i` 的牌数。
     *
     * 多出来的那几类固定分配给编号最小的若干个类型；该细节在 M1 固定，
     * 并作为 [BoardGenerator] 与测试的断言依据。
     */
    fun countsFor(tileCount: Int, typeCount: Int): List<Int> {
        require(typeCount >= 1) { "图案种类必须 >= 1，实际为 $typeCount" }
        require(tileCount % 2 == 0) { "牌总数必须为偶数，实际为 $tileCount" }
        require(tileCount >= typeCount * 2) {
            "牌总数不足以让每类图案至少分到 2 张：tileCount=$tileCount, typeCount=$typeCount"
        }

        val quotient = tileCount / typeCount
        val base = quotient - (quotient % 2) // 向下取到最近的偶数
        val extraTypes = (tileCount - base * typeCount) / 2

        return List(typeCount) { type -> if (type < extraTypes) base + 2 else base }
    }
}
