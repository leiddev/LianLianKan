package com.ldxy.lianliankan.ui.theme

import com.ldxy.lianliankan.domain.config.LevelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤注册表的不变量（SRS FR-13.7 / NFR-4.2 / FR-11.4）。
 *
 * 由 M6 的 `TileFacesTest` 迁移而来，既有断言全部保留，并新增注册表一致性检查。
 *
 * **无法在此覆盖的一项**：`LocalTileSkin` 的默认值（计划里的风险 R-2）——
 * `CompositionLocal.defaultValue` 是 Compose 内部 API，单测取不到。
 * 这里改为断言 `TileSkins.default` 本身自洽（非空、图案数足够），
 * 因为默认值就取自它；实际渲染效果需在真机或 `@Preview` 上确认。
 */
class SkinTest {

    private val maxTypeCount = LevelCatalog.levels.maxOf { it.typeCount }

    // ============================================================ 注册表

    @Test
    fun `皮肤编号唯一且与下标一致`() {
        // byId 按 id 线性查找、all 按顺序排列，两者混用时最容易出错的就是「下标 ≠ id」
        val ids = TileSkins.all.map { it.id }
        assertEquals("皮肤编号存在重复", ids.size, ids.toSet().size)
        assertEquals("皮肤应按 id 升序排列且与下标一致", ids.indices.toList(), ids)
    }

    @Test
    fun `默认皮肤存在且编号为 0`() {
        assertEquals(0, TileSkins.DEFAULT_ID)
        assertEquals(TileSkins.all[0], TileSkins.default)
        assertEquals(TileSkins.default, TileSkins.byId(TileSkins.DEFAULT_ID))
    }

    @Test
    fun `每套皮肤都有展示名`() {
        for (skin in TileSkins.all) {
            assertTrue("皮肤 ${skin.id} 缺少名称资源", skin.nameRes != 0)
        }
    }

    @Test
    fun `三套皮肤已注册`() {
        assertEquals("蔬果 / 动物 / 符号", 3, TileSkins.all.size)
    }

    // ============================================================ 图案集

    @Test
    fun `每套皮肤的图案数量都不少于关卡所需的最大种类数`() {
        for (skin in TileSkins.all) {
            assertTrue(
                "皮肤 ${skin.id} 只有 ${skin.faces.size} 个图案，但关卡最多需要 $maxTypeCount 个，" +
                    "会出现取模回绕导致同一关出现重复图案",
                skin.faces.size >= maxTypeCount,
            )
        }
    }

    @Test
    fun `每套皮肤内部图案互不相同且非空`() {
        for (skin in TileSkins.all) {
            assertEquals(
                "皮肤 ${skin.id} 内存在重复图案",
                skin.faces.size,
                skin.faces.toSet().size,
            )
            assertTrue("皮肤 ${skin.id} 存在空图案", skin.faces.none { it.isBlank() })
        }
    }

    @Test
    fun `皮肤两两不同 - 同一类型在不同皮肤下给出不同图案`() {
        val types = 0 until maxTypeCount
        for (first in TileSkins.all) {
            for (second in TileSkins.all) {
                if (first.id >= second.id) continue
                for (type in types) {
                    assertTrue(
                        "皮肤 ${first.id} 与 ${second.id} 在类型 $type 上是同一图案，切换皮肤将无效果",
                        first.faceAt(type) != second.faceAt(type),
                    )
                }
            }
        }
    }

    @Test
    fun `每个关卡的每一种图案类型都能取到图案`() {
        for (skin in TileSkins.all) {
            for (config in LevelCatalog.levels) {
                for (type in 0 until config.typeCount) {
                    assertNotNull(
                        "皮肤 ${skin.id} 第 ${config.level} 关类型 $type 取不到图案",
                        skin.faceAt(type),
                    )
                }
            }
        }
    }

    // ============================================================ 边界

    @Test
    fun `未注册的皮肤编号回退到默认皮肤`() {
        assertEquals(TileSkins.default, TileSkins.byId(99))
        assertEquals(TileSkins.default, TileSkins.byId(-1))
        assertEquals(TileSkins.default, TileSkins.byId(Int.MIN_VALUE))
        assertEquals(TileSkins.default, TileSkins.byId(Int.MAX_VALUE))
    }

    @Test
    fun `负数与越界的图案类型不抛异常`() {
        for (skin in TileSkins.all) {
            assertNotNull(skin.faceAt(-1))
            assertNotNull(skin.faceAt(Int.MIN_VALUE))
            // 越界类型按取模回绕，仍落在该皮肤内
            assertTrue(skin.faceAt(skin.faces.size) in skin.faces)
            assertTrue(skin.faceAt(skin.faces.size * 3 + 1) in skin.faces)
        }
    }

    @Test
    fun `faceAt 对范围内的类型返回对应图案`() {
        for (skin in TileSkins.all) {
            for ((index, expected) in skin.faces.withIndex()) {
                assertEquals("皮肤 ${skin.id} 类型 $index", expected, skin.faceAt(index))
            }
        }
    }
}
