package com.ldxy.lianliankan.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ldxy.lianliankan.R
import com.ldxy.lianliankan.domain.session.GameResult

/** 暂停弹窗（SRS FR-10.1：继续 / 重开本关 / 返回主菜单）。 */
@Composable
fun PauseDialog(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text(stringResource(R.string.dialog_paused)) },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.action_resume))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRestart) {
                    Text(stringResource(R.string.action_restart_level))
                }
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.action_back_to_menu))
                }
            }
        },
    )
}

/**
 * 结算面板（SRS FR-10.2 – FR-10.6）。
 *
 * 展示本关得分、最高连击、时间奖励与最佳分刷新状态（FR-10.4）；
 * 通关时提供「下一关」（末关显示「全部通关」），失败时提供「重试本关」「返回主菜单」。
 *
 * @param hasNextLevel 是否还有下一关（末关为 `false`）。
 * @param isNewRecord 是否刷新了该关最佳分（FR-8.5）。
 */
@Composable
fun ResultDialog(
    result: GameResult,
    hasNextLevel: Boolean,
    isNewRecord: Boolean,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        // 结算面板是模态的：必须显式选一个动作，不允许点外部关掉（FR-10.5 / FR-10.6）
        onDismissRequest = { },
        title = {
            Text(
                stringResource(
                    if (result.isWin) R.string.dialog_win else R.string.dialog_lose,
                ),
            )
        },
        text = {
            Column {
                ResultLine(stringResource(R.string.result_score), result.score.toString())
                ResultLine(stringResource(R.string.result_max_combo), "x${result.maxCombo}")
                ResultLine(stringResource(R.string.result_time_bonus), "+${result.timeBonus}")
                if (isNewRecord) {
                    Text(
                        text = stringResource(R.string.result_new_record),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            when {
                result.isWin && hasNextLevel -> TextButton(onClick = onNextLevel) {
                    Text(stringResource(R.string.action_next_level))
                }

                result.isWin -> TextButton(onClick = onExit) {
                    // 末关：没有「下一关」，改为「全部通关」并直接回主菜单（FR-10.5）
                    Text(stringResource(R.string.result_all_cleared))
                }

                else -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.action_back_to_menu))
            }
        },
    )
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = "  $value", style = MaterialTheme.typography.bodyMedium)
    }
}
