package com.ldxy.lianliankan.ui.game

/**
 * 牌面图案集（SRS FR-13.7：使用 emoji 与矢量图标，须保证辨识度）。
 *
 * 抽成独立对象是为了满足 SRS NFR-4.2「新增图案/皮肤无需改动核心逻辑」：
 * 图案类型 `Int` 与显示字符的映射只存在于这一处，`GameSession` 等逻辑完全不感知。
 *
 * M10 起按皮肤编号组织（SRS FR-11.4，P2）：[facesFor] 按 `skinId` 取图案集，
 * 未注册的编号回退到默认皮肤。**切换皮肤的界面尚未实现**，这里只预留资源结构 ——
 * 后续新增皮肤只需往 [SKINS] 里加一组图案，并让设置页把 `skinId` 传下来即可。
 */
object TileFaces {

    /** 默认皮肤（0）。13 个图案对应 SRS 9.3 中最大的 `typeCount`。 */
    private val FRUITS: List<String> = listOf(
        "🍎", "🍋", "🍇", "🍓", "🍑", "🍒", "🥝",
        "🍊", "🍌", "🥕", "🌽", "🍄", "🥑",
    )

    /** 备用皮肤（1）：与默认皮肤图案数量一致，便于直接替换。 */
    private val ANIMALS: List<String> = listOf(
        "🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐸",
    )

    private val SKINS: List<List<String>> = listOf(FRUITS, ANIMALS)

    /** 已注册的皮肤数量。 */
    val skinCount: Int get() = SKINS.size

    /** 默认皮肤编号。 */
    const val DEFAULT_SKIN_ID: Int = 0

    /** 取皮肤编号对应的图案集；编号未注册时回退到默认皮肤。 */
    fun facesFor(skinId: Int): List<String> = SKINS.getOrElse(skinId) { FRUITS }

    /** 取某皮肤下图案类型对应的显示字符。 */
    fun faceFor(type: Int, skinId: Int = DEFAULT_SKIN_ID): String {
        val faces = facesFor(skinId)
        // 用 Int.mod 而非 %，保证类型为负时也能得到合规下标（逻辑层不产生负类型，
        // 但这里不冒抛异常的风险）
        return faces[type.mod(faces.size)]
    }

    /** 图案集中的条目数（以默认皮肤为准），用于断言「不少于关卡所需的最大种类数」。 */
    val size: Int get() = FRUITS.size
}
