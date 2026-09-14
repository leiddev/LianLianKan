package com.ldxy.lianliankan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ldxy.lianliankan.data.DataStoreProgressRepository
import com.ldxy.lianliankan.data.DataStoreSettingsRepository
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.data.SettingsRepository
import com.ldxy.lianliankan.data.appDataStore
import com.ldxy.lianliankan.domain.model.Settings
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
        // 边到边（V1.1 改进 I-4）。
        //
        // targetSdk = 37 意味着 Android 15（API 35）起系统**已强制**边到边，且
        // android:statusBarColor 被弃用、设置无效。这里显式开启，是为了让 Android 14
        // 及以下设备也保持一致行为 —— 否则会出现「新系统压住顶部内容、旧系统不压」这种
        // 只在部分设备上复现的问题。
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContent {
            // DataStore 的首个值需要读盘，因此必须给初始值；用 SRS 7.2 的默认值即可，
            // 读到真实值后会自动切换到用户设置。
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            // 状态栏图标（时间 / 信号 / 电量）恒为深色（I-4）。
            // 边到边之后状态栏是透明的，露出的是 APP 自己的渐变背景，因此
            // 「状态栏颜色与 APP 统一」是自动达成的，不需要再设 statusBarColor。
            // V1.3 起应用只有浅色配色（4 种主题色都很浅），图标恒为深色才不会与背景糊在一起。
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = true
            }

            LianLianKanTheme(
                // 主题配色与皮肤同样从设置流下来，因此切换后重组即生效（SRS FR-11.3 / FR-11.6）
                paletteId = settings.themePaletteId,
                skinId = settings.skinId,
            ) {
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
