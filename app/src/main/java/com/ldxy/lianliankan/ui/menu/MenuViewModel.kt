package com.ldxy.lianliankan.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.domain.config.LevelCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 主菜单状态（SRS FR-1.1 / FR-1.2）。
 *
 * @param entryLevel 点「开始 / 继续」时进入的关卡：有进行中进度则回到该关，
 *   否则从首个未通关关卡开始（已全通则回到第 1 关）。
 * @param showContinue 按钮文案：存在未完成进度时显示「继续游戏」。
 */
data class MenuUiState(
    val entryLevel: Int = 1,
    val showContinue: Boolean = false,
    val maxUnlockedLevel: Int = 1,
    val hasRecords: Boolean = false,
)

/** 主菜单 ViewModel：只读进度，负责算出「进入哪一关」（SRS FR-1.2）。 */
class MenuViewModel(
    progressRepository: ProgressRepository,
) : ViewModel() {

    val uiState: StateFlow<MenuUiState> = progressRepository.progress
        .map { progress ->
            val lastLevel = LevelCatalog.levelCount
            val inProgress = progress.inProgressLevel
            MenuUiState(
                entryLevel = inProgress
                    ?: progress.maxUnlockedLevel.coerceIn(1, lastLevel),
                showContinue = inProgress != null,
                maxUnlockedLevel = progress.maxUnlockedLevel,
                hasRecords = progress.levelBestScores.isNotEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MenuUiState(),
        )

    companion object {
        fun factory(progressRepository: ProgressRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MenuViewModel(progressRepository) }
            }
    }
}
