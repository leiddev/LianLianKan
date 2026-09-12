package com.ldxy.lianliankan.domain.model

/** 主题模式（SRS FR-11.3）。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 用户设置（SRS 7.1 / FR-11）。
 *
 * 默认值与 SRS 7.2 数据字典一致：音效与振动默认开启，主题默认跟随系统，皮肤默认第 0 套。
 * 持久化由 M8 的 DataStore 仓库负责，本类保持为纯 Kotlin 数据。
 */
data class Settings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val skinId: Int = 0,
)
