package com.ldxy.lianliankan.ui.about

import com.ldxy.lianliankan.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关于界面的信息组装测试（V1.6 / SRS FR-15；对应计划 §5.1 的 A-1 ~ A-3）。
 *
 * ### 这三条断言在防什么
 * 关于界面本身很薄（就是几行文字），但它有一个**必然会发生**的失效方式：
 * **版本号被写死在界面或文案里**。真到了发版那天，改 `build.gradle.kts` 的人未必会想起
 * 还有一个地方抄了一份版本号，而这个界面恰恰是「用来告诉别人这是哪一版」的 ——
 * 它一旦说谎，比没有这个界面更糟。
 *
 * 因此 [展示的版本号必须来自构建产物] 这条断言的作用不是「验证格式化函数」，而是
 * **把「硬编码」变成一次会红的构建**：只要有人把 `"1.6"` 写进代码，下次改版本号时它就会失败。
 * 这是本项目「把已经踩过的坑写成断言」这一习惯的延续（见 V1.5 计划 §19.2）。
 *
 * 本文件在纯 JVM 下运行：[aboutVersionLabel] 不依赖任何 Android 框架，
 * [appInfoFromBuild] 读的 [BuildConfig] 是编译期常量。
 */
class AboutInfoTest {

    @Test
    fun `版本文字按「版本名（构建 号）」排版 - 且退化输入不崩`() {
        // 正常输入：同时给出版本名与构建号 —— 构建号是为了回答「设备上装的是哪一版」
        // （V1.5 有一轮返工正因为无法在应用内确认版本而多花了一轮）
        assertEquals("1.6（构建 12）", aboutVersionLabel(AppInfo("1.6", 12)))

        // 版本名两侧的空白应被去掉
        assertEquals("1.6（构建 12）", aboutVersionLabel(AppInfo("  1.6  ", 12)))

        // 退化的版本名：用兜底文字，而不是显示一个空白行。
        // 注意此时**仍然保留**构建号 —— 版本名缺失时构建号是唯一还能定位包的信息，丢掉更糟。
        assertEquals("$UNKNOWN_VERSION_NAME（构建 12）", aboutVersionLabel(AppInfo("", 12)))
        assertEquals("$UNKNOWN_VERSION_NAME（构建 12）", aboutVersionLabel(AppInfo("   ", 12)))

        // 退化的构建号：0 表示「未设置」，负数无意义 —— 两者都省略「（构建 n）」这一段，
        // 而不是显示「（构建 0）」这种会让人误以为真的有过 0 号构建的文字
        assertEquals("1.6", aboutVersionLabel(AppInfo("1.6", 0)))
        assertEquals("1.6", aboutVersionLabel(AppInfo("1.6", -1)))
        assertEquals(UNKNOWN_VERSION_NAME, aboutVersionLabel(AppInfo("", 0)))
    }

    @Test
    fun `展示的版本号必须来自构建产物 - 不得写死`() {
        // ★ 本文件最要紧的一条：断言里没有任何字面量版本号，比较对象就是 BuildConfig 本身。
        // 若有人把版本号写死在 aboutVersionLabel 里，下次改 build.gradle.kts 的 versionName 时
        // 这条就会失败。
        val info = appInfoFromBuild()
        val label = aboutVersionLabel(info)

        assertTrue(
            "关于界面展示的版本文字「$label」里不含 BuildConfig.VERSION_NAME" +
                "（${BuildConfig.VERSION_NAME}）—— 版本号疑似被写死了",
            label.contains(BuildConfig.VERSION_NAME),
        )
        assertTrue(
            "关于界面展示的版本文字「$label」里不含 BuildConfig.VERSION_CODE" +
                "（${BuildConfig.VERSION_CODE}）—— 构建号疑似被写死了",
            label.contains(BuildConfig.VERSION_CODE.toString()),
        )
    }

    @Test
    fun `构建配置里的版本信息齐全`() {
        // 防「构建配置漏填」：版本名为空、或还是 Android 默认的占位值、或构建号没设，
        // 都会让关于界面显示出没有意义的内容。
        val info = appInfoFromBuild()

        assertTrue(
            "BuildConfig.VERSION_NAME 为空 —— 请检查 app/build.gradle.kts 的 versionName",
            info.versionName.isNotBlank(),
        )
        assertTrue(
            "BuildConfig.VERSION_NAME（${info.versionName}）不像一个版本号（应形如 1.6）—— " +
                "请检查 app/build.gradle.kts 的 versionName",
            Regex("""\d+(\.\d+)*""").containsMatchIn(info.versionName),
        )
        assertTrue(
            "BuildConfig.VERSION_CODE 为 ${info.versionCode}，应为正数 —— " +
                "请检查 app/build.gradle.kts 的 versionCode",
            info.versionCode > 0,
        )
    }
}
