package com.ldxy.lianliankan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.data.DataStoreProgressRepository
import com.ldxy.lianliankan.data.DataStoreSettingsRepository
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.data.SettingsRepository
import com.ldxy.lianliankan.data.appDataStore
import com.ldxy.lianliankan.domain.model.Settings
import com.ldxy.lianliankan.domain.model.ThemeMode
import com.ldxy.lianliankan.ui.nav.AppNavHost
import com.ldxy.lianliankan.ui.theme.LianLianKanTheme
import com.ldxy.lianliankan.ui.theme.appBackgroundBrush

/**
 * 应用唯一 Activity（SRS FR-1.4：锁定竖屏，见 AndroidManifest.xml）。
 *
 * 同时充当最轻量的依赖容器：M8 起注入 DataStore 实现的仓库（SRS FR-14.1 / FR-14.2），
 * 界面层对此无感知。
 */
class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext.appDataStore)
    }
    private val progressRepository: ProgressRepository by lazy {
        DataStoreProgressRepository(applicationContext.appDataStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // DataStore 的首个值需要读盘，因此必须给初始值；用 SRS 7.2 的默认值即可，
            // 读到真实值后会自动切换到用户设置。
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            LianLianKanTheme(darkTheme = settings.themeMode.resolveDarkTheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appBackgroundBrush()),
                ) {
                    AppNavHost(
                        settingsRepository = settingsRepository,
                        progressRepository = progressRepository,
                        onExitApp = { finish() },
                    )
                }
            }
        }
    }
}

/**
 * 主题模式 → 是否使用深色（SRS FR-11.3：跟随系统 / 浅色 / 深色）。
 *
 * 判定放在这里而不是 `Settings` 里，是因为「跟随系统」需要读 Compose 的环境，
 * 而领域模型必须保持为可在 JVM 上单测的纯 Kotlin（SRS NFR-3.1）。
 */
@Composable
private fun ThemeMode.resolveDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
