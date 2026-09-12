package com.ldxy.lianliankan.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ldxy.lianliankan.data.ProgressRepository
import com.ldxy.lianliankan.data.SettingsRepository
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.model.Settings
import com.ldxy.lianliankan.feedback.AndroidSoundPlayer
import com.ldxy.lianliankan.feedback.AndroidVibratorPlayer
import com.ldxy.lianliankan.feedback.FeedbackDispatcher
import com.ldxy.lianliankan.ui.game.GameScreen
import com.ldxy.lianliankan.ui.game.GameViewModel
import com.ldxy.lianliankan.ui.level.LevelSelectScreen
import com.ldxy.lianliankan.ui.level.LevelSelectViewModel
import com.ldxy.lianliankan.ui.menu.MenuScreen
import com.ldxy.lianliankan.ui.menu.MenuViewModel
import com.ldxy.lianliankan.ui.settings.SettingsScreen
import com.ldxy.lianliankan.ui.settings.SettingsViewModel

/** 路由表（SRS 5.1 的界面清单：主菜单 / 关卡选择 / 游戏 / 设置）。 */
object Route {
    const val MENU = "menu"
    const val LEVELS = "levels"
    const val SETTINGS = "settings"
    const val GAME = "game"
    const val ARG_LEVEL = "level"

    fun game(level: Int) = "$GAME/$level"
}

/**
 * 应用导航图（开发计划 M7-1）。
 *
 * 屏幕本身不做导航决策，只接收回调；「进入哪一关」这类判断由各 ViewModel 依据进度算好
 * （SRS FR-1.2），保持 SRS NFR-3.1 的分层意图。
 *
 * @param onExitApp 「退出应用」（SRS FR-1.3），由调用方决定如何结束（Activity.finish）。
 */
@Composable
fun AppNavHost(
    settingsRepository: SettingsRepository,
    progressRepository: ProgressRepository,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // 音效与振动反馈（SRS FR-12）。开关用 lambda 读取最新的设置值，
    // 这样玩家在设置里关掉后立即生效，而不是用到构造时的快照（FR-11.6 / FR-12.3）。
    val context = LocalContext.current
    val settings by settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = Settings())
    val latestSettings by rememberUpdatedState(settings)

    val feedback = remember(context) {
        FeedbackDispatcher(
            soundPlayer = AndroidSoundPlayer(context),
            vibratorPlayer = AndroidVibratorPlayer(context),
            soundEnabled = { latestSettings.soundEnabled },
            vibrationEnabled = { latestSettings.vibrationEnabled },
        )
    }
    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }

    NavHost(
        navController = navController,
        startDestination = Route.MENU,
        modifier = modifier,
    ) {
        composable(Route.MENU) {
            val viewModel: MenuViewModel = viewModel(
                factory = MenuViewModel.factory(progressRepository),
            )
            MenuScreen(
                viewModel = viewModel,
                onStartGame = { level -> navController.navigate(Route.game(level)) },
                onLevelSelect = { navController.navigate(Route.LEVELS) },
                onSettings = { navController.navigate(Route.SETTINGS) },
                onExit = onExitApp,
            )
        }

        composable(Route.LEVELS) {
            val viewModel: LevelSelectViewModel = viewModel(
                factory = LevelSelectViewModel.factory(progressRepository),
            )
            LevelSelectScreen(
                viewModel = viewModel,
                onLevelSelected = { level -> navController.navigate(Route.game(level)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(settingsRepository, progressRepository),
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Route.GAME}/{${Route.ARG_LEVEL}}",
            arguments = listOf(navArgument(Route.ARG_LEVEL) { type = NavType.IntType }),
        ) { entry ->
            val level = entry.arguments?.getInt(Route.ARG_LEVEL) ?: 1
            val config = LevelCatalog.levels.firstOrNull { it.level == level }
                ?: LevelCatalog.firstLevel

            val viewModel: GameViewModel = viewModel(
                key = "game-${config.level}",
                factory = GameViewModel.factory(config),
            )

            // 记录「进行中的关卡」，供主菜单显示「继续游戏」（SRS FR-1.2 / FR-14.3）
            LaunchedEffect(config.level) {
                progressRepository.setInProgressLevel(config.level)
            }

            GameScreen(
                viewModel = viewModel,
                progressRepository = progressRepository,
                feedback = feedback,
                onExitToMenu = { navController.popBackStack(Route.MENU, inclusive = false) },
                onNextLevel = { next ->
                    navController.navigate(Route.game(next)) {
                        // 通关后跳下一关：把当前关卡从回退栈里移除，避免层层堆叠
                        popUpTo(Route.game(config.level)) { inclusive = true }
                    }
                },
            )
        }
    }
}
