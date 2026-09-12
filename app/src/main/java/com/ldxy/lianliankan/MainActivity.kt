package com.ldxy.lianliankan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ldxy.lianliankan.ui.theme.LianLianKanTheme

/**
 * 应用唯一 Activity（SRS FR-1.4：锁定竖屏，见 AndroidManifest.xml）。
 *
 * M0 阶段仅用于验证 Compose 基线可用；M7 将接入 Navigation 承载
 * 主菜单 / 关卡选择 / 游戏 / 设置等屏幕。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LianLianKanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaceholderScreen()
                }
            }
        }
    }
}

/** M0 占位页面，M7 起被真实导航图替换。 */
@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    LianLianKanTheme {
        PlaceholderScreen()
    }
}
