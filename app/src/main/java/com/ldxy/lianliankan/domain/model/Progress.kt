package com.ldxy.lianliankan.domain.model

/**
 * 关卡进度与成绩（SRS 7.1 / FR-2 / FR-14.3）。
 *
 * 对应 SRS 7.2 数据字典中的 `max_unlocked_level`、`level_best_score_<n>`、
 * `total_best_score`、`in_progress_level` 四项。
 */
data class Progress(
    /** 已解锁的最高关卡（SRS FR-2.4）。默认 1，即开局仅第 1 关可玩。 */
    val maxUnlockedLevel: Int = 1,
    /** 各关最佳得分，键为关卡编号（SRS FR-2.2）。 */
    val levelBestScores: Map<Int, Int> = emptyMap(),
    /** 历史最高总分（SRS 9.2）。 */
    val totalBestScore: Int = 0,
    /** 进行中的关卡编号，`null` 表示无进行中进度（SRS FR-1.2 / FR-14.3）。 */
    val inProgressLevel: Int? = null,
) {
    fun bestScoreOf(level: Int): Int = levelBestScores[level] ?: 0

    fun isUnlocked(level: Int): Boolean = level in 1..maxUnlockedLevel

    /** 记录通关结果：刷新最佳分并解锁下一关（SRS FR-2.4 / FR-8.5）。 */
    fun recordCleared(level: Int, score: Int, levelCount: Int): Progress = copy(
        maxUnlockedLevel = minOf(maxOf(maxUnlockedLevel, level + 1), levelCount),
        levelBestScores = levelBestScores + (level to maxOf(bestScoreOf(level), score)),
        totalBestScore = maxOf(totalBestScore, score),
        inProgressLevel = null,
    )
}
