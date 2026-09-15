package com.ldxy.lianliankan.ui.about

import com.ldxy.lianliankan.BuildConfig

/**
 * 关于界面要展示的应用信息（V1.6 / SRS FR-15）。
 *
 * ### 为什么做成数据类 + 纯函数
 * 「版本号从哪来」是本版本唯一有技术含量的决策，而它的**唯一真源**是
 * `app/build.gradle.kts` 的 `versionName` / `versionCode`（经 `buildConfig = true`
 * 投影成 [BuildConfig]）。把「取版本」与「排版版本文字」拆成两层的好处是：
 *
 * - [appInfoFromBuild] 只负责取值，界面只负责展示 —— 界面拿不到「自己造一个版本号」的机会；
 * - [aboutVersionLabel] 是**纯函数**，因此「版本号是怎么排版的」可以被 JVM 单测直接钉住
 *   （见 `AboutInfoTest`），其中包括一条专门防「把版本号写死在代码里」的断言。
 *
 * 本文件不 import 任何 `android.*` / `androidx.*`（SRS NFR-3.1 的分层要求；
 * [BuildConfig] 只是本模块的构建产物，不是 Android 框架类）。
 *
 * @param versionName 版本名，如 `"1.6"`
 * @param versionCode 构建号，如 `12`
 */
data class AppInfo(
    val versionName: String,
    val versionCode: Int,
)

/** 取不到版本名时的兜底文字（正常构建下不会出现，仅用于让退化输入也有可读输出）。 */
const val UNKNOWN_VERSION_NAME = "未知版本"

/**
 * 版本号的展示文字，形如 `1.6（构建 12）`。
 *
 * 同时展示版本名与构建号，是为了回答真机反馈时最常缺的那个问题：**设备上装的到底是哪一版**。
 * V1.5 的一轮返工就卡在这里（当时只能靠逐版递增 `versionCode` 来区分包，无法在应用内确认）。
 *
 * 退化输入不抛异常：版本名为空时用 [UNKNOWN_VERSION_NAME] 兜底；构建号非正数时省略「（构建 n）」
 * 这一段（`versionCode` 为 0 表示未设置，而不是「第 0 版」）。
 * 两段互相独立 —— 版本名缺失时**仍然保留**构建号，因为那时它是唯一还能定位到包的信息。
 */
fun aboutVersionLabel(info: AppInfo): String {
    val name = info.versionName.trim().ifBlank { UNKNOWN_VERSION_NAME }
    return if (info.versionCode > 0) "$name（构建 ${info.versionCode}）" else name
}

/**
 * 从构建产物取当前应用信息。
 *
 * [BuildConfig.VERSION_NAME] / [BuildConfig.VERSION_CODE] 由 `app/build.gradle.kts` 生成，
 * 因此这里**不需要**任何 `PackageManager` 查询（也就没有 API 33+ 那个被弃用的重载问题）。
 */
fun appInfoFromBuild(): AppInfo = AppInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
)
