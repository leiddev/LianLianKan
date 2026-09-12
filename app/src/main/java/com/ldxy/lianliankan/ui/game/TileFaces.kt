package com.ldxy.lianliankan.ui.game

/**
 * 牌面图案集（SRS FR-13.7：使用 emoji 与矢量图标，须保证辨识度）。
 *
 * 抽成独立对象是为了满足 SRS NFR-4.2「新增图案/皮肤无需改动核心逻辑」：
 * 图案类型 `Int` 与显示字符的映射只存在于这一处，[GameSession] 等逻辑完全不感知。
 *
 * **不变量**：条目数必须不少于 [com.ldxy.lianliankan.domain.config.LevelCatalog] 中最大的
 * `typeCount`（当前为 13）。`TileFacesTest` 会断言这一点，避免新增关卡时悄悄撞上取模回绕。
 */
object TileFaces {

    private val FACES: List<String> = listOf(
        "🍎", "🍋", "🍇", "🍓", "🍑", "🍒", "🥝",
        "🍊", "🍌", "🥕", "🌽", "🍄", "🥑",
    )

    val size: Int get() = FACES.size

    /**
     * 取图案类型对应的显示字符。
     *
     * 用 [Int.mod] 而非 `%`，保证类型为负时也能得到合规下标（逻辑层不会产生负类型，
     * 但这里不冒抛异常的风险）。
     */
    fun faceFor(type: Int): String = FACES[type.mod(FACES.size)]
}
