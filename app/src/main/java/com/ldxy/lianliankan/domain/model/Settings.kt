package com.ldxy.lianliankan.domain.model

/**
 * 用户设置（SRS 7.1 / FR-11）。
 *
 * 默认值与 SRS 7.2 数据字典一致：音效与振动默认开启，主题为第 0 套配色，皮肤默认第 0 套。
 * 持久化由 M8 的 DataStore 仓库负责，本类保持为纯 Kotlin 数据。
 */
data class Settings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /**
     * 主题配色编号（SRS FR-11.3）。
     *
     * V1.3 起主题是「4 种主题色」而非旧的「深浅三态」，深色模式已整体取消
     * （见 V1.3 计划的 Q2-c），因此这里存的是一个编号而不是枚举。
     *
     * 默认值 0 与 `ThemePalettes.DEFAULT_ID` 一致 —— **直接写字面量而不是引用它**，
     * 是因为 `domain` 层不得依赖 `ui`（SRS NFR-3.1）；两者的一致性由
     * `ThemePaletteContrastTest` 的「默认编号应为 0」固定住。
     */
    val themePaletteId: Int = 0,
    val skinId: Int = 0,
)
