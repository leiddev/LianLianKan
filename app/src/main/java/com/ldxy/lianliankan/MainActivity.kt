package com.ldxy.lianliankan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ldxy.lianliankan.data.DataStoreProgressRepository
import com.ldxy.lianliankan.data.DataStoreSettingsRepository
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.data.SettingsRepository
import com.ldxy.lianliankan.data.appDataStore
import com.ldxy.lianliankan.domain.model.Settings
import com.ldxy.lianliankan.ui.SplashGate
import com.ldxy.lianliankan.ui.nav.AppNavHost
import com.ldxy.lianliankan.ui.theme.AnimatedBackground
import com.ldxy.lianliankan.ui.theme.BACKGROUND_DIAGNOSTIC
import com.ldxy.lianliankan.ui.theme.BackgroundDiagnosticOverlay
import com.ldxy.lianliankan.ui.theme.LianLianKanTheme
import kotlinx.coroutines.launch

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

    /**
     * 启动画面是否可以退场了（V1.4 需求 ②）。
     *
     * 只在主线程读写：写方是 [onCreate] 里那个 `lifecycleScope` 协程（主调度器），
     * 读方是系统在主线程每帧询问的放行条件，因此不需要任何同步手段。
     */
    private var isSettingsLoaded = false

    /**
     * 启动画面期间抓到的**首个真实设置**（可能为 null —— 超时兜底路径）。
     *
     * 它让 `setContent` 的**首次组合**就能用上玩家选的配色，而不是先按
     * [Settings] 的默认配色组合一帧、等 DataStore 的值到了再重组跳色。
     */
    private var initialSettings: Settings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动画面必须在 `super.onCreate` **之前**安装：晚一行，系统就已经用
        // 窗口主题画过首帧，那层白底照样会闪一下 —— 这正是需求 ② 要消除的东西。
        // 放行条件用「设置是否已读到首值」而不是时间（见下面的兜底说明）。
        installSplashScreen().setKeepOnScreenCondition { !isSettingsLoaded }

        // 边到边（V1.1 改进 I-4）。
        //
        // targetSdk = 37 意味着 Android 15（API 35）起系统**已强制**边到边，且
        // android:statusBarColor 被弃用、设置无效。这里显式开启，是为了让 Android 14
        // 及以下设备也保持一致行为 —— 否则会出现「新系统压住顶部内容、旧系统不压」这种
        // 只在部分设备上复现的问题。
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // 放行启动画面的两条路径（V1.4 需求 ②）。
        //
        // ① 正常路径：仓库报「已读到首个真实值」并通过同一份数据取回该值。
        //    不等的话，界面会先按默认配色渲染、再跳到玩家实际选的配色，
        //    那是一次肉眼可见的颜色跳变，与白屏叠加在一起。
        // ② 兜底路径：超时也放行。**这一条不可省略** —— DataStore 因文件损坏等原因
        //    读不出值时，放行条件会永远为 false，启动画面就永远留在屏幕上，
        //    「读不到设置」于是变成「应用起不来」（V1.4 计划风险 R-3，等级高）。
        //
        // 逻辑放在 ui/SplashGate.kt 而不是这里，是为了让这两条路径都能被 JVM 单测
        // 覆盖（在 Activity 里就只能靠真机验收了）。
        lifecycleScope.launch {
            initialSettings = SplashGate.awaitSettings(settingsRepository)
            isSettingsLoaded = true
        }

        setContent {
            // 首次组合用启动画面期间抓到的真实设置（超时兜底路径下它是 null，
            // 退回 SRS 7.2 的默认值，随后由数据到达触发的重组纠正）。
            // 用 remember 固定：之后的变化一律以 DataStore 的流为准。
            val initial = remember { initialSettings ?: Settings() }
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = initial)

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
                Box(modifier = Modifier.fillMaxSize()) {
                    // 动态背景（V1.5 / SRS FR-13.9）：铺满全屏，且必须在所有界面内容**之下** ——
                    // [Box] 的子级按声明顺序绘制，所以它是第一个子级。
                    // 只有前台 RESUMED 时它才会真正运动（内部用生命周期门控，见 AnimatedBackground.kt）。
                    AnimatedBackground(modifier = Modifier.fillMaxSize())

                    AppNavHost(
                        settingsRepository = settingsRepository,
                        progressRepository = progressRepository,
                        onExitApp = { finish() },
                    )

                    // ⚠️ 临时诊断叠加层（V1.5 排查用）：必须在**最后一个**子级 —— 它要能在
                    // 背景层被任何东西盖住时仍然可见。确诊后连同 BackgroundDiagnostic.kt 一起删除。
                    if (BACKGROUND_DIAGNOSTIC) {
                        BackgroundDiagnosticOverlay(
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            }
        }
    }
}
