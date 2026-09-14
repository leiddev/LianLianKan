package com.ldxy.lianliankan.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.R
import com.ldxy.lianliankan.ui.game.TileFaceBox
import com.ldxy.lianliankan.ui.theme.ThemePalette
import com.ldxy.lianliankan.ui.theme.ThemePalettes
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

            // 主题配色（SRS FR-11.3 / V1.3）。
            // 色块而不是文字单选：主题的差别就是颜色本身，让玩家看着颜色选。
            Text(
                text = stringResource(R.string.settings_theme_color),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePalettes.all.forEach { palette ->
                    ThemeSwatch(
                        palette = palette,
                        selected = settings.themePaletteId == palette.id,
                        onClick = { viewModel.onThemePaletteChange(palette.id) },
                    )
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

/**
 * 一个圆形主题色块（SRS FR-11.3）。
 *
 * 填充的是该配色方案的 `primary` 而非种子色 —— 玩家在界面上真正看到并与之交互的
 * 就是 `primary`（按钮、选中高亮），色块必须与之一致，否则选了「浅蓝」却发现按钮是别的蓝。
 *
 * 选中态用 `onSurface` 描边（而不是 `primary`）：`onSurface` 与任何一套配色的 `primary`
 * 都拉开了明度差，因此描边在 4 种颜色上都清晰。
 */
@Composable
private fun ThemeSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 读屏需要能念出「浅蓝 / 黄绿 / ...」而不是「theme-1」，因此用字符串资源而不是编号。
    // 色块本身没有可见文字，contentDescription 是它唯一的无障碍入口。
    val label = stringResource(palette.nameRes)
    Box(
        modifier = Modifier
            .size(THEME_SWATCH_SIZE)
            .clip(CircleShape)
            .background(palette.light.primary)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** 主题色块边长：够大便于点按，一行 4 个在竖屏下也不会换行。 */
private val THEME_SWATCH_SIZE = 48.dp

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
