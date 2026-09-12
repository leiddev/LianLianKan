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
}
