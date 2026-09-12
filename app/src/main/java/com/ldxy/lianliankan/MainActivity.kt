package com.ldxy.lianliankan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.ui.game.GameScreen
import com.ldxy.lianliankan.ui.game.GameViewModel
import com.ldxy.lianliankan.ui.theme.LianLianKanTheme

/**
 * 应用唯一 Activity（SRS FR-1.4：锁定竖屏，见 AndroidManifest.xml）。
 *
 * M6 阶段直接承载第 1 关的游戏界面，用于验证棋盘渲染、选中、连线与消除动画；
 * M7 将改为导航宿主，由主菜单 / 关卡选择进入具体关卡。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LianLianKanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val gameViewModel: GameViewModel = viewModel(
                        factory = GameViewModel.factory(LevelCatalog.firstLevel),
                    )
                    GameScreen(viewModel = gameViewModel)
                }
            }
        }
    }
}
