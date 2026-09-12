package com.ldxy.lianliankan.ui.game

import com.ldxy.lianliankan.domain.config.LevelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图案集的不变量（SRS FR-13.7 / NFR-4.2）：新增关卡时不能悄悄撞上取模回绕。 */
class TileFacesTest {

    @Test
    fun `图案数量不少于关卡所需的最大种类数`() {
        val maxTypeCount = LevelCatalog.levels.maxOf { it.typeCount }
        assertTrue(
            "图案集只有 ${TileFaces.size} 个，但关卡最多需要 $maxTypeCount 个，会出现重复图案",
            TileFaces.size >= maxTypeCount,
        )
    }

    @Test
    fun `所有图案互不相同`() {
        val faces = (0 until TileFaces.size).map { TileFaces.faceFor(it) }
        assertEquals("图案集中存在重复项", faces.size, faces.toSet().size)
        assertTrue("不应有空图案", faces.none { it.isBlank() })
    }

    @Test
    fun `每个关卡的每一种图案类型都能取到图案`() {
        for (config in LevelCatalog.levels) {
            for (type in 0 until config.typeCount) {
                assertNotNull(
                    "第 ${config.level} 关类型 $type 取不到图案",
                    TileFaces.faceFor(type),
                )
            }
        }
    }

    @Test
    fun `负数类型不会抛异常`() {
        assertNotNull(TileFaces.faceFor(-1))
        assertNotNull(TileFaces.faceFor(Int.MIN_VALUE))
    }

    // ============================================================ 皮肤结构（FR-11.4，P2）

    @Test
    fun `每套皮肤的图案数量都不少于关卡所需`() {
        val maxTypeCount = LevelCatalog.levels.maxOf { it.typeCount }
        for (skinId in 0 until TileFaces.skinCount) {
            assertTrue(
                "皮肤 $skinId 的图案数不足 $maxTypeCount",
                TileFaces.facesFor(skinId).size >= maxTypeCount,
            )
        }
    }

    @Test
    fun `不同皮肤的同一类型给出不同图案`() {
        assertTrue("至少应有 2 套皮肤用于验证结构", TileFaces.skinCount >= 2)
        for (type in 0 until TileFaces.size) {
            assertTrue(
                "皮肤 0 与皮肤 1 在类型 $type 上不应是同一图案，否则皮肤切换无效果",
                TileFaces.faceFor(type, skinId = 0) != TileFaces.faceFor(type, skinId = 1),
            )
        }
    }

    @Test
    fun `未注册的皮肤编号回退到默认皮肤`() {
        val fallback = TileFaces.facesFor(TileFaces.DEFAULT_SKIN_ID)

        assertEquals(fallback, TileFaces.facesFor(99))
        assertEquals(fallback, TileFaces.facesFor(-1))
        assertEquals(
            TileFaces.faceFor(3, TileFaces.DEFAULT_SKIN_ID),
            TileFaces.faceFor(3, skinId = 99),
        )
    }

    @Test
    fun `每套皮肤内部图案互不相同`() {
        for (skinId in 0 until TileFaces.skinCount) {
            val faces = TileFaces.facesFor(skinId)
            assertEquals("皮肤 $skinId 内存在重复图案", faces.size, faces.toSet().size)
            assertTrue("皮肤 $skinId 存在空图案", faces.none { it.isBlank() })
        }
    }
}
