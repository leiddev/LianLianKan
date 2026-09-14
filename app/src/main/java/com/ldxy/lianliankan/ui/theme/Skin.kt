package com.ldxy.lianliankan.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import com.ldxy.lianliankan.R

/**
 * 一套皮肤：图案集 + 展示名（SRS FR-11.4 / FR-13.7）。
 *
 * Level 1 只承载图案；若将来让皮肤同时携带配色（SRS FR-11.4 的「图案/配色主题」），
 * 只需在本类追加字段并让 [LianLianKanTheme] 多消费一层 —— 调用方无需改动。
 */
data class TileSkin(
    val id: Int,
    /** 设置页展示用的名字。用资源 id 而非字符串，便于将来本地化。 */
    @StringRes val nameRes: Int,
    /** 该皮肤的全部图案，长度须不少于关卡所需的最大 `typeCount`（见 `SkinTest`）。 */
    val faces: List<String>,
) {
    /**
     * 取图案类型对应的显示字符。
     *
     * 用 [Int.mod] 而非 `%`，保证类型为负时也能得到合规下标（逻辑层不产生负类型，
     * 但这里不冒抛异常的风险）。
     */
    fun faceAt(type: Int): String = faces[type.mod(faces.size)]
}

/**
 * 皮肤注册表（SRS NFR-4.2：新增图案/皮肤无需改动核心逻辑）。
 *
 * 新增一套皮肤只需往 [all] 追加一项并加一条字符串资源，
 * `GameSession` 等逻辑层完全不感知皮肤的存在。
 *
 * **定位说明**：图案集最初放在 `ui/game`（名为 `TileFaces`），V1.2 迁到 `ui/theme` ——
 * 因为设置页的预览也要用它，它已属于「跨屏幕的呈现配置」，与 `Color` / `Shape` / `Theme` 同类。
 */
object TileSkins {

    /** 默认皮肤编号。 */
    const val DEFAULT_ID = 0

    /**
     * 全部皮肤。
     *
     * 三套的取舍：
     * - **蔬果（默认）**：13 个图案，色彩丰富、辨识度高，作为默认观感；
     * - **动物**：表情类 emoji，与蔬果风格区分明显；
     * - **符号**：普通 Unicode 几何字符，**由系统字体渲染，跨设备一致性远好于 emoji**，
     *   兼作 emoji 支持不佳设备上的兜底（见 V1.2 计划的风险 R-1）。
     */
    val all: List<TileSkin> = listOf(
        TileSkin(
            id = 0,
            nameRes = R.string.skin_produce,
            faces = listOf(
                "🍎", "🍋", "🍇", "🍓", "🍑", "🍒", "🥝",
                "🍊", "🍌", "🥕", "🌽", "🍄", "🥑",
            ),
        ),
        TileSkin(
            id = 1,
            nameRes = R.string.skin_animals,
            faces = listOf(
                "🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼",
                "🐨", "🐯", "🦁", "🐮", "🐷", "🐸",
            ),
        ),
        TileSkin(
            id = 2,
            nameRes = R.string.skin_symbols,
            // 刻意避开 ♥ ♠ ♣ ♦ ❄ 这类「有 emoji 变体」的字符 —— 它们在部分设备上会被
            // 渲染成彩色 emoji，正是本皮肤想避免的情况。这里全部取几何图形/装饰符号。
            faces = listOf(
                "▲", "▼", "■", "●", "◆", "★", "✚",
                "✦", "✿", "◐", "▣", "◈", "⬢",
            ),
        ),
    )

    /** 默认皮肤。 */
    val default: TileSkin get() = all[DEFAULT_ID]

    /** 按编号取皮肤；**未注册的编号回退到默认皮肤**，不抛异常。 */
    fun byId(id: Int): TileSkin = all.firstOrNull { it.id == id } ?: default
}

/**
 * 当前皮肤。
 *
 * 用 CompositionLocal 而非参数透传：[游戏界面] 与 [设置页预览] 必须共用同一来源，
 * 否则迟早出现「预览与实际不一致」；而读它的只有叶子组件 `TileView`，
 * 棋盘最多渲染约 96 个，正是环境式呈现配置的典型场景。
 *
 * 用 [compositionLocalOf] 而不是 `staticCompositionLocalOf`：后者值一变就重组整个
 * content 子树，而皮肤切换只需要让真正读它的那批 `TileView` 重组，
 * 没必要让 HUD、操作条、连线画布跟着重组。
 *
 * 默认值非空，保证 `@Preview` 或未 provide 的场景不会崩溃或串皮肤（计划的风险 R-2）。
 */
val LocalTileSkin = compositionLocalOf { TileSkins.default }
