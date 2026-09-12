package com.ldxy.lianliankan.domain.config

/**
 * 单关配置（SRS 7.1，取值见附录 9.3）。
 *
 * 全部关卡参数集中在 [LevelCatalog] 中定义，避免硬编码（SRS NFR-3.2 / NFR-4.1）。
 *
 * @param level 关卡编号，从 1 开始。
 * @param cols 棋盘列数（SRS 9.3 中「棋盘(列×行)」的宽）。
 * @param rows 棋盘行数（同上，为高）。
 * @param typeCount 图案种类数。
 * @param timeLimitSeconds 限时秒数（SRS FR-7.1）。
 * @param hintCount 本关提示道具次数（SRS FR-9.1）。
 * @param shuffleCount 本关洗牌道具次数（SRS FR-9.2）。
 */
data class LevelConfig(
    val level: Int,
    val cols: Int,
    val rows: Int,
    val typeCount: Int,
    val timeLimitSeconds: Int,
    val hintCount: Int,
    val shuffleCount: Int,
) {
    /** 牌总数，等于 [cols] × [rows]（SRS 附录 9.3）。 */
    val tileCount: Int get() = cols * rows

    init {
        require(level >= 1) { "关卡编号必须从 1 开始，实际为 $level" }
        require(cols > 0 && rows > 0) { "棋盘尺寸必须为正：${cols}x$rows" }
        require(typeCount >= 1) { "图案种类必须 >= 1，实际为 $typeCount" }
        require(timeLimitSeconds > 0) { "限时必须为正，实际为 $timeLimitSeconds" }
        require(hintCount >= 0 && shuffleCount >= 0) { "道具次数不得为负" }
        // SRS FR-3.1：牌总数与每类图案数量均为偶数。
        // 张数分配规则（开发计划 D-1）要求牌总数为偶数，且每类至少分到 2 张。
        require(tileCount % 2 == 0) { "第 $level 关牌总数为奇数：$tileCount" }
        require(tileCount >= typeCount * 2) {
            "第 $level 关牌总数不足以让每类图案至少分到 2 张：tileCount=$tileCount, typeCount=$typeCount"
        }
    }
}
