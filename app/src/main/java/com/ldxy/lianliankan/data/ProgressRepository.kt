package com.ldxy.lianliankan.data

import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Progress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 关卡进度与成绩仓库（SRS FR-2.2 / FR-2.4 / FR-10.4 / FR-14）。
 *
 * 与 [SettingsRepository] 同理：M7 面向接口编程，M8 换成 DataStore 实现。
 */
interface ProgressRepository {

    val progress: StateFlow<Progress>

    /**
     * 记录一次通关：刷新最佳分并解锁下一关（SRS FR-2.4 / FR-8.5）。
     *
     * @return 是否刷新了该关的最佳分，供结算面板提示（SRS FR-10.4）。
     */
    suspend fun recordCleared(level: Int, score: Int): Boolean

    /** 保存「进行中的关卡」，供主菜单的「继续游戏」使用（SRS FR-1.2 / FR-14.3）。 */
    suspend fun setInProgressLevel(level: Int?)

    /** 清除全部进度（SRS FR-11.5）。 */
    suspend fun clear()
}

/** 内存实现：M7 阶段的占位，M8 换成 DataStore 后仅保留给测试使用。 */
class InMemoryProgressRepository(
    initial: Progress = Progress(),
) : ProgressRepository {

    private val state = MutableStateFlow(initial)
    override val progress: StateFlow<Progress> = state.asStateFlow()

    override suspend fun recordCleared(level: Int, score: Int): Boolean {
        val before = state.value.bestScoreOf(level)
        state.update { it.recordCleared(level, score, LevelCatalog.levelCount) }
        return score > before
    }

    override suspend fun setInProgressLevel(level: Int?) {
        state.update { it.copy(inProgressLevel = level) }
    }

    override suspend fun clear() {
        state.value = Progress()
    }
}
