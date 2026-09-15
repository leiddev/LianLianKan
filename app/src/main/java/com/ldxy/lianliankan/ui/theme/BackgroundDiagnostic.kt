package com.ldxy.lianliankan.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Text

/**
 * ⚠️ **临时诊断代码 —— 确诊后整个文件与三处调用一起删除。**
 *
 * ### 为什么需要它
 * V1.5 的动态背景连续三轮真机反馈都是「看不到」，而本机是虚拟化环境、起不了模拟器，
 * 也就是说：**改动方（我）没有任何办法观察到渲染结果**。三轮排查都建立在推理上，
 * 而推理已经两次被真机推翻（像素差算错、动画被系统时长缩放掐死）。
 * 因此改为把「看不到」这件事拆成几个**屏幕上能直接读数**的问题，一次截图即可定位：
 *
 * | 读数 | 它回答的问题 | 若不是预期值意味着 |
 * |---|---|---|
 * | `vc` | 设备上跑的到底是不是这一版 | 装包/构建路径的问题，代码怎么改都没用 |
 * | `anim` | 系统「动画程序时长缩放」当前值 | 若为 0，本项目**其它** Compose 动画也全是瞬变 |
 * | `frames` | Compose 的帧回调是否在派发（本层自己数帧） | 数字不动 → 帧时钟没走，任何 `withFrameNanos` 动画都不会动 |
 * | `state` | 当前生命周期状态 | 若一直不是 RESUMED → 前台门控判断有问题 |
 * | 画布上的洋红标记 | 背景这一层**是否真的在画**、画在哪 | 看不到洋红 → 这一层根本没画出来（被盖住/没组合） |
 * | 洋红色的圆圈轮廓 | 光斑的圆心与半径是否落在预期位置 | 圈在屏幕外 → 位置公式有问题 |
 *
 * ### 怎么看
 * 主菜单截一张图即可：洋红方框贴着屏幕四边、左上角一块洋红方块、几个洋红圆圈，
 * 以及左上角一块黄底黑字的读数。再隔 5 秒截第二张，对比 `frames` 是否增长、
 * 洋红圆圈是否移动、光斑是否移动。
 */
const val BACKGROUND_DIAGNOSTIC = true

/** 诊断标记用的颜色：洋红在任何主题色下都不可能被误认为界面元素。 */
internal val DiagnosticMagenta = Color(0xFFFF00FF)

/**
 * 诊断读数叠加层：**放在所有界面内容之上**（`MainActivity` 的最后一个子级），
 * 这样即使背景层被别的东西盖住，这几个数字也仍然看得见 —— 那本身就是关键信息。
 */
@Composable
fun BackgroundDiagnosticOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 装的是哪一版
    val versionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "$code/${info.versionName}"
        }.getOrElse { "?" }
    }

    // 系统「动画程序时长缩放」—— Compose 会按它折算所有动画时长（见 V1.5 计划 §16）
    val animatorScale = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrElse { -1f }
    }

    // 本层自己数帧：用与背景时钟完全相同的机制，因此它就是那个时钟的探针
    var frames by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            frames++
        }
    }

    val state = LocalLifecycleOwner.current.lifecycle.currentState

    Column(
        modifier = modifier
            .background(Color(0xFFFFF176))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "诊断 vc=$versionLabel anim=$animatorScale",
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "frames=$frames state=$state",
            color = Color.Black,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
