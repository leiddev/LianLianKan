package com.ldxy.lianliankan.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ldxy.lianliankan.R

/**
 * 关于界面（V1.6 / SRS FR-15）。
 *
 * 展示 APP 名称、版本号与版权信息 —— 三件事分别对应三类用途：作品集展示、问题反馈
 * （版本号）、归属声明（版权）。详见 `doc/V1.6改进计划.md` §1。
 *
 * ### 两条来自真实事故的约束（本文件遵守）
 * 1. **不铺不透明底色**：动态背景铺在所有界面之下（`MainActivity` 的 `Box` 第一子级），
 *    任何一层画了不透明满屏底色就会把背景整层盖住 —— V1.5 曾在关卡选择与设置两屏上
 *    真实发生过（`Scaffold` 的默认 `containerColor` 即 `colorScheme.background`）。
 *    因此这里显式传 `Color.Transparent`，并把该点写进计划的风险表 R-1 与验收项 V-5。
 * 2. **版本号只能来自构建产物**：本界面接收 [AppInfo] 而不是自己取版本，
 *    更不允许写死（见 [aboutVersionLabel] 与 `AboutInfoTest` 的断言）。
 *
 * ### 为什么图标只显示前景层
 * 用 V1.4 生成的自适应图标前景层（矢量，任何密度下都清晰，且已按 66dp 安全区作画，
 * 不会被裁切），不额外生成资源。代价是画布本身是 108dp、内容只占中间 66dp，
 * 因此同一个尺寸下看着会比常见 APP 的图标小一圈（V1.4 的启动图标也有同一取舍）。
 * 若真机上觉得偏小，把它换成一个「背景层 + 按 1.636 倍放大的前景层 + 圆角裁剪」的
 * 组合图标即可（约六行，不需要新资源）。
 *
 * @param info 要展示的应用信息，由调用方从构建产物取（见 [appInfoFromBuild]）。
 * @param onBack 返回上一级；顶部返回按钮与系统返回键都应能触发（后者由导航图负责）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    info: AppInfo,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 不铺自己的底色（见文件头约束 1）：让 APP 级的动态背景透上来。
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_about)) },
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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                // 装饰性图形：名称就在它下方，读屏时重复念一遍没有信息量
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 名称与桌面图标同名，且与主菜单标题同源（都是 R.string.app_name），
            // 因此不存在「关于界面叫一个名字、桌面图标叫另一个」的可能
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_version, aboutVersionLabel(info)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
