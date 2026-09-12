package com.ldxy.lianliankan.domain.config

/**
 * 全部关卡配置表，逐行对应 SRS 附录 9.3「关卡难度配置表」。
 *
 * 表中「棋盘(列×行)」按 SRS 原注「规格选用宽度 × 高度」解读，
 * 因此每关的牌总数 = 列数 × 行数，与 9.3 表「牌总数」列一一吻合。
 * 新增关卡只需在此追加一行（SRS NFR-4.1）。
 */
object LevelCatalog {

    val levels: List<LevelConfig> = listOf(
        //   关卡  列  行  图案  限时  提示  洗牌
        level(1, 6, 8, 8, 180, 3, 3),
        level(2, 6, 8, 9, 180, 3, 3),
        level(3, 8, 8, 9, 180, 3, 3),
        level(4, 6, 10, 10, 180, 3, 3),
        level(5, 8, 8, 10, 175, 3, 3),
        level(6, 8, 10, 10, 170, 3, 3),
        level(7, 8, 10, 11, 165, 3, 3),
        level(8, 8, 10, 12, 160, 3, 3),
        level(9, 8, 12, 12, 155, 2, 2),
        level(10, 8, 12, 13, 150, 2, 2),
    )

    val levelCount: Int get() = levels.size

    val firstLevel: LevelConfig get() = levels.first()

    val lastLevel: LevelConfig get() = levels.last()

    /** 取指定关卡配置；编号非法时抛出带说明的异常。 */
    fun configOf(level: Int): LevelConfig =
        levels.firstOrNull { it.level == level }
            ?: throw IllegalArgumentException("不存在的关卡编号：$level（有效范围 1..$levelCount）")

    /** 该关卡是否存在于配置表。 */
    fun hasLevel(level: Int): Boolean = levels.any { it.level == level }

    private fun level(
        level: Int,
        cols: Int,
        rows: Int,
        typeCount: Int,
        timeLimitSeconds: Int,
        hintCount: Int,
        shuffleCount: Int,
    ) = LevelConfig(
        level = level,
        cols = cols,
        rows = rows,
        typeCount = typeCount,
        timeLimitSeconds = timeLimitSeconds,
        hintCount = hintCount,
        shuffleCount = shuffleCount,
    )
}
