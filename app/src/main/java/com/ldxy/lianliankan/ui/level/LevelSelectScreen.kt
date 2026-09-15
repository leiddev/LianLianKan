package com.ldxy.lianliankan.ui.level

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.R

/**
 * 关卡选择（SRS FR-2）。
 *
 * 未解锁关卡卡片不可点击、以弱化配色呈现（FR-2.3）；已通关关卡显示最佳分（FR-2.2）。
 * 解锁状态完全来自 [LevelSelectViewModel] 映射的进度，界面不做判断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(
    viewModel: LevelSelectViewModel,
    onLevelSelected: (level: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 不铺自己的底色（V1.5 修订 1）：[Scaffold] 的默认 containerColor 是
        // colorScheme.background，那是一个**不透明**的满屏 Surface —— 会把 APP 级的
        // 动态背景整层盖掉（这一屏因此完全看不到背景动画）。给透明色即让出这一层，
        // 与主菜单、游戏界面（两者本来就没有 Scaffold）保持一致。
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_level_select)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.levels, key = { it.config.level }) { item ->
                LevelCard(
                    item = item,
                    onClick = { onLevelSelected(item.config.level) },
                )
            }
        }
    }
}

@Composable
private fun LevelCard(
    item: LevelItem,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = item.unlocked,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.unlocked) colors.surfaceVariant else colors.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.level_number, item.config.level),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.unlocked) colors.onSurfaceVariant else colors.outline,
                )
            }
            Text(
                text = stringResource(
                    R.string.level_size,
                    item.config.cols,
                    item.config.rows,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.outline,
            )
            Text(
                text = if (item.unlocked) {
                    stringResource(R.string.level_best, item.bestScore)
                } else {
                    stringResource(R.string.level_locked)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.unlocked && item.bestScore > 0) colors.primary else colors.outline,
            )
        }
    }
}
