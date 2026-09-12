package com.ldxy.lianliankan.ui.level

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.config.LevelConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 关卡选择列表项（SRS FR-2.1 / FR-2.2 / FR-2.3）。
 *
 * @param unlocked 未解锁的关卡只展示锁定态、不可进入。
 * @param bestScore 已通关关卡的最佳得分；未通关为 0。
 */
data class LevelItem(
    val config: LevelConfig,
    val unlocked: Boolean,
    val bestScore: Int,
)

data class LevelSelectUiState(
    val levels: List<LevelItem> = LevelCatalog.levels.map {
        LevelItem(config = it, unlocked = it.level == 1, bestScore = 0)
    },
)

/** 关卡选择 ViewModel：把进度映射成「解锁态 + 最佳分」（SRS FR-2）。 */
class LevelSelectViewModel(
    progressRepository: ProgressRepository,
) : ViewModel() {

    val uiState: StateFlow<LevelSelectUiState> = progressRepository.progress
        .map { progress ->
            LevelSelectUiState(
                levels = LevelCatalog.levels.map { config ->
                    LevelItem(
                        config = config,
                        unlocked = progress.isUnlocked(config.level),
                        bestScore = progress.bestScoreOf(config.level),
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevelSelectUiState(),
        )

    companion object {
        fun factory(progressRepository: ProgressRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LevelSelectViewModel(progressRepository) }
            }
    }
}
