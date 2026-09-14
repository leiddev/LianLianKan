package com.ldxy.lianliankan.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ldxy.lianliankan.R
import com.ldxy.lianliankan.domain.session.GameState

/**
 * 顶部状态栏（SRS 5.1：当前关卡、倒计时、当前得分、连击）。
 *
 * 剩余时间低于 30 秒时整块变红（SRS FR-7.4）。是否告警由领域层的
 * `GameState.isTimeLow` 给出，UI 不重复判定阈值。
 *
 * ### 状态栏区域的背景（V1.1 改进）
 * [contentInsets] 施加在 **Surface 内部的 Row** 上，而不是 Surface 自身 ——
 * 这样 Surface 的背景色会一直向上铺到屏幕顶端（含状态栏区域），
 * 而内容仍避开状态栏。若把插边加在 Surface 上，状态栏区域就会露出 APP 的渐变背景，
 * 与 HUD 形成一条色带。
 */
@Composable
fun GameHud(
    state: GameState,
    modifier: Modifier = Modifier,
    contentInsets: WindowInsets = WindowInsets.statusBars,
) {
    val colors = MaterialTheme.colorScheme
    val timeColor by animateColorAsState(
        targetValue = if (state.isTimeLow) colors.error else colors.onSurface,
        animationSpec = tween(durationMillis = 300),
        label = "timeColor",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(contentInsets)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 当前关卡（V1.1 改进 I-5）。直接取 config.level，不另存状态，
            // 因此从「关卡选择」/「下一关」/「重试本关」任一路径进入都必然显示正确。
            HudItem(
                label = stringResource(R.string.hud_level),
                value = state.config.level.toString(),
                valueColor = colors.secondary,
            )
            HudItem(
                label = stringResource(R.string.hud_time),
                value = formatTime(state.timeLeftSeconds),
                valueColor = timeColor,
            )
            HudItem(
                label = stringResource(R.string.hud_score),
                value = state.score.points.toString(),
            )
            HudItem(
                label = stringResource(R.string.hud_combo),
                value = if (state.score.combo > 0) "x${state.score.combo}" else "-",
                // 连击是瞬时状态，用主色调让它更跳
                valueColor = if (state.score.combo > 0) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HudItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 底部操作条（SRS 5.1：提示、洗牌、暂停）。
 *
 * 道具次数用尽时按钮置灰且不可点击（SRS FR-9.3）—— 可用性直接取自 `GameState`，
 * 不在 UI 侧重算。
 */
@Composable
fun GameActionBar(
    state: GameState,
    onHintClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PropButton(
                label = stringResource(R.string.action_hint),
                remaining = state.hintLeft,
                enabled = state.hintLeft > 0 && state.isPlaying,
                onClick = onHintClick,
                modifier = Modifier.weight(1f),
            )
            PropButton(
                label = stringResource(R.string.action_shuffle),
                remaining = state.shuffleLeft,
                enabled = state.shuffleLeft > 0 && state.isPlaying,
                onClick = onShuffleClick,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onPauseClick,
                enabled = state.isPlaying,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_pause))
            }
        }
    }
}

@Composable
private fun PropButton(
    label: String,
    remaining: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(label)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($remaining)",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 秒数格式化为 `M:SS`（SRS FR-7.1 的倒计时展示）。 */
internal fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return "%d:%02d".format(minutes, rest)
}
