package com.ldxy.lianliankan.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.R

/**
 * 主菜单（SRS FR-1.1 – FR-1.3）。
 *
 * 「开始游戏 / 继续游戏」的文案与目标关卡都由 [MenuViewModel] 依据进度算好
 * （SRS FR-1.2），界面只负责展示与转发点击。
 */
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onStartGame: (level: Int) -> Unit,
    onLevelSelect: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onStartGame(state.entryLevel) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state.showContinue) R.string.menu_continue else R.string.menu_start,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLevelSelect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.menu_level_select))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.menu_settings))
        }

        Spacer(modifier = Modifier.height(40.dp))

        TextButton(onClick = onExit) {
            Text(stringResource(R.string.menu_exit))
        }
    }
}
