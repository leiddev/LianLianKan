package com.ldxy.lianliankan.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.R
import com.ldxy.lianliankan.domain.model.ThemeMode
import com.ldxy.lianliankan.ui.game.TileFaceBox
import com.ldxy.lianliankan.ui.theme.TileSkin
import com.ldxy.lianliankan.ui.theme.TileSkins

/**
 * 设置页（SRS FR-11）。
 *
 * 修改立即生效并持久化（FR-11.6）—— 由 [SettingsViewModel] 写仓库，
 * 界面不等确认、不做本地副本。清除进度带二次确认（FR-11.5）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SwitchRow(
                label = stringResource(R.string.settings_sound),
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::onSoundEnabledChange,
            )
            SwitchRow(
                label = stringResource(R.string.settings_vibration),
                checked = settings.vibrationEnabled,
                onCheckedChange = viewModel::onVibrationEnabledChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
            )
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.onThemeModeChange(mode) },
                        )
                        Text(text = themeModeLabel(mode))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 皮肤（SRS FR-11.4 / V1.2 的 J-3）。
            // 刻意不用主题那样的文字单选 —— 皮肤是视觉选择，纯文字等于让玩家盲选。
            Text(
                text = stringResource(R.string.settings_skin),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TileSkins.all.forEach { skin ->
                    SkinCard(
                        skin = skin,
                        selected = settings.skinId == skin.id,
                        onClick = { viewModel.onSkinIdChange(skin.id) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            TextButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_progress),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_progress)) },
            text = { Text(stringResource(R.string.settings_clear_progress_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onClearProgress()
                        confirmClear = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/**
 * 一张皮肤卡片：左侧渲染该皮肤的 3 个示例牌面，右侧是皮肤名与选中标记。
 *
 * 预览直接复用棋盘的 `TileFaceBox`（V1.2 的 J-1）—— 因此不需要伪造 `Tile` 领域对象，
 * 也不会出现「预览与实际不一致」。预览牌面不可点击。
 */
@Composable
private fun SkinCard(
    skin: TileSkin,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceVariant,
        ),
        border = if (selected) {
            BorderStroke(2.dp, colors.primary)
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(skin.nameRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 取该皮肤的前 3 个图案作为示例
                skin.faces.take(PREVIEW_FACE_COUNT).forEach { face ->
                    TileFaceBox(face = face, cellSize = PREVIEW_CELL_SIZE, enabled = false)
                }
            }
        }
    }
}

/** 预览卡片里渲染几个示例牌面。 */
private const val PREVIEW_FACE_COUNT = 3

/** 预览牌面的边长；比棋盘上的格子略小，避免卡片过高。 */
private val PREVIEW_CELL_SIZE = 44.dp
