package com.ldxy.lianliankan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.data.SettingsRepository
import com.ldxy.lianliankan.domain.model.Settings
import com.ldxy.lianliankan.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页 ViewModel（SRS FR-11.6：修改后立即生效并持久化）。 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Settings(),
        )

    fun onSoundEnabledChange(enabled: Boolean) = launch { settingsRepository.setSoundEnabled(enabled) }

    fun onVibrationEnabledChange(enabled: Boolean) =
        launch { settingsRepository.setVibrationEnabled(enabled) }

    fun onThemeModeChange(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }

    fun onSkinIdChange(skinId: Int) = launch { settingsRepository.setSkinId(skinId) }

    /** 清除进度（SRS FR-11.5，需二次确认 —— 由界面负责弹确认框）。 */
    fun onClearProgress() = launch { progressRepository.clear() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            progressRepository: ProgressRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(settingsRepository, progressRepository) }
        }
    }
}
