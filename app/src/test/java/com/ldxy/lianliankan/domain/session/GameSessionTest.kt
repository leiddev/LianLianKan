package com.ldxy.lianliankan.domain.session

import com.ldxy.lianliankan.domain.at
import com.ldxy.lianliankan.domain.board.BoardFactory
import com.ldxy.lianliankan.domain.board.BoardGenerator
import com.ldxy.lianliankan.domain.board.DeadlockDetector
import com.ldxy.lianliankan.domain.boardOf
import com.ldxy.lianliankan.domain.config.LevelCatalog
import com.ldxy.lianliankan.domain.config.LevelConfig
import com.ldxy.lianliankan.domain.model.GamePhase
import com.ldxy.lianliankan.domain.model.TileState
import com.ldxy.lianliankan.domain.path.PathFinder
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话级集成测试（开发计划 M5-8）：
 * 在真实棋盘上把 M1 ~ M4 的零件串起来跑，验证 SRS UC-03 的事务顺序、3.4 的状态流程与
 * FR-6 / FR-7 / FR-8 / FR-9 / FR-10 的行为。
 */
class GameSessionTest {

    private companion object {
        const val SEED = 20260912L
    }

    private class FakeClock {
        var now: Long = 0L
            private set

        fun advance(millis: Long) {
            now += millis
        }

        fun asClock(): () -> Long = { now }
    }

    private class Harness(
        val config: LevelConfig,
        factory: BoardFactory,
    ) {
        val clock = FakeClock()
        val session = GameSession(config = config, boardFactory = factory, clock = clock.asClock())
    }

    private fun generatedHarness(config: LevelConfig = LevelCatalog.configOf(1)) =
        Harness(config, BoardGenerator(Random(SEED)))

    private fun fixedHarness(board: String, vararg moreRows: String): Harness {
        val rows = arrayOf(board) + moreRows
        val config = LevelConfig(
            level = 1,
            cols = rows[0].length,
            rows = rows.size,
            typeCount = rows.joinToString("").filter { it != '.' }.toSet().size,
            timeLimitSeconds = 180,
            hintCount = 3,
            shuffleCount = 3,
        )
        return Harness(config) { boardOf(*rows) }
    }

    // ============================================================ 开局（FR-9.5）

    @Test
    fun `开局后进入 PLAYING 并按配置初始化`() {
        val h = generatedHarness()
        val result = h.session.start()

        assertEquals(GamePhase.PLAYING, h.session.state.phase)
        assertEquals(h.config.tileCount, h.session.state.remainingTiles)
        assertEquals(h.config.hintCount, h.session.state.hintLeft)
        assertEquals(h.config.shuffleCount, h.session.state.shuffleLeft)
        assertEquals(h.config.timeLimitSeconds, h.session.state.timeLeftSeconds)
        assertEquals(0, h.session.state.score.points)
        assertNull(h.session.state.selectedPosition)
        assertNull("进行中不应有结算数据", h.session.state.result)
        assertFalse("正常生成不应触发自动重排", result.autoShuffled)
    }

    @Test
    fun `READY 阶段不响应任何操作`() {
        val h = generatedHarness()

        assertEquals(GamePhase.READY, h.session.state.phase)
        assertTrue(h.session.select(at(0, 0)) is SelectResult.Ignored)
        assertTrue(h.session.useHint() is HintResult.NotAvailable)
        assertTrue(h.session.useShuffle() is ShuffleResult.NotAvailable)
    }

    // ============================================================ 选中（FR-4.1 / FR-4.2）

    @Test
    fun `选中与取消选中`() {
        val h = generatedHarness()
        h.session.start()

        val move = DeadlockDetector.findMove(h.session.state.board)!!
        val position = move.first.position

        assertEquals(SelectResult.Selected, h.session.select(position))
        assertEquals(position, h.session.state.selectedPosition)
        assertEquals(
            TileState.SELECTED,
            h.session.state.board.tileAt(position)!!.state,
        )

        assertEquals(SelectResult.Deselected, h.session.select(position))
        assertNull(h.session.state.selectedPosition)
        assertEquals(TileState.NORMAL, h.session.state.board.tileAt(position)!!.state)
    }

    @Test
    fun `点击空格或越界坐标被忽略`() {
        // 右下角两格留空，用于验证「点到空格」的行为
        val h = fixedHarness("AABB", "CC..")
        h.session.start()

        h.session.select(at(0, 0))
        assertEquals(at(0, 0), h.session.state.selectedPosition)

        assertEquals("空格应被忽略", SelectResult.Ignored, h.session.select(at(1, 2)))
        assertEquals("越界坐标应被忽略", SelectResult.Ignored, h.session.select(at(9, 9)))
        assertEquals("被忽略的点击不应改变选中态", at(0, 0), h.session.state.selectedPosition)
        assertEquals("被忽略的点击不应改变牌数", 6, h.session.state.remainingTiles)
    }

    // ============================================================ 消除（FR-4.3 / FR-8.1）

    @Test
    fun `相邻同类型牌消除并获得基础分`() {
        val h = fixedHarness("AABB", "CCDD")
        h.session.start()

        assertEquals(SelectResult.Selected, h.session.select(at(0, 0)))
        val result = h.session.select(at(0, 1))

        assertTrue("应消除成功", result is SelectResult.Eliminated)
        val eliminated = result as SelectResult.Eliminated
        assertEquals("首次消除得基础分", 10, eliminated.gainedPoints)
        assertEquals(1, eliminated.combo)
        assertFalse("本局未进入死局，不应自动重排", eliminated.autoShuffled)
        assertEquals("直线路径应为 0 拐点", 0, eliminated.path.turns)

        assertEquals(6, h.session.state.remainingTiles)
        assertEquals(10, h.session.state.score.points)
        assertNull("消除后不应残留选中态", h.session.state.selectedPosition)
    }

    @Test
    fun `连击窗口内的连续消除提升得分`() {
        val h = fixedHarness("AABB", "CCDD")
        h.session.start()

        h.session.select(at(0, 0))
        h.session.select(at(0, 1))
        assertEquals(1, h.session.state.score.combo)

        h.clock.advance(1_000)
        h.session.select(at(0, 2))
        val second = h.session.select(at(0, 3))

        assertEquals(2, (second as SelectResult.Eliminated).combo)
        assertEquals(2, h.session.state.score.combo)
        assertEquals("连击 2 的单次得分为 11", 11, second.gainedPoints)
        assertEquals(21, h.session.state.score.points)
    }

    // ============================================================ 错误反馈（FR-4.4 / FR-4.5）

    @Test
    fun `图案不同时判为错误并把第二张设为选中且连击归零`() {
        val h = fixedHarness("AABB", "CCDD")
        h.session.start()

        // 先建立一次连击
        h.session.select(at(0, 0))
        h.session.select(at(0, 1))
        assertEquals(1, h.session.state.score.combo)
        val pointsBefore = h.session.state.score.points

        // 图案不同 → 错误操作
        h.session.select(at(0, 2))
        val wrong = h.session.select(at(1, 2))

        assertTrue(wrong is SelectResult.WrongType)
        assertEquals(at(0, 2), (wrong as SelectResult.WrongType).first)
        assertEquals(at(1, 2), wrong.second)

        assertEquals("第二张牌应被设为选中", at(1, 2), h.session.state.selectedPosition)
        assertEquals("连击应归零", 0, h.session.state.score.combo)
        assertEquals("得分不应变化", pointsBefore, h.session.state.score.points)
        assertEquals("不应消除任何牌", 6, h.session.state.remainingTiles)
    }

    @Test
    fun `图案相同但不可连通时不消除并保留第一张的选中态`() {
        // 2x3 棋盘：A 与 B 各自对角、被彼此堵死（需 3 拐点），只有竖直相邻的 C 可消
        val h = fixedHarness("ABC", "BAC")
        h.session.start()

        assertNotNull("前置条件：棋盘上应有可行步（C）", DeadlockDetector.findMove(h.session.state.board))
        assertNull(
            "前置条件：A 对在 2 拐点内不可连通",
            PathFinder.find(h.session.state.board, at(0, 0), at(1, 1)),
        )

        h.session.select(at(0, 0))
        val result = h.session.select(at(1, 1))

        assertTrue(result is SelectResult.NoPath)
        assertEquals(at(0, 0), (result as SelectResult.NoPath).first)
        assertEquals(at(1, 1), result.second)

        assertEquals(
            "应保留第一张的选中态（FR-4.4 / UC-03 3.b.ii）",
            at(0, 0),
            h.session.state.selectedPosition,
        )
        assertEquals("不应消除任何牌", 6, h.session.state.remainingTiles)
        assertEquals("连击应归零", 0, h.session.state.score.combo)
    }

    @Test
    fun `连通失败后可直接改选其他搭档`() {
        // A 有 6 张、B 有 2 张。A(0,0) 与对角 A(1,1) 被两个 B 堵死；
        // 但 A(0,0) 与 A(0,2) 可绕棋盘上方连通。
        val h = fixedHarness("ABAA", "BAAA")
        h.session.start()

        h.session.select(at(0, 0))
        assertTrue("第一对不可连通", h.session.select(at(1, 1)) is SelectResult.NoPath)
        assertEquals("第一张应仍处于选中态", at(0, 0), h.session.state.selectedPosition)

        // 不重新点第一张，直接改选另一个搭档
        val result = h.session.select(at(0, 2))

        assertTrue("换搭档后应能成功消除，实际为 $result", result is SelectResult.Eliminated)
        assertEquals(6, h.session.state.remainingTiles)
        assertNull("消除后应清空选中态", h.session.state.selectedPosition)
    }

    // ============================================================ 死局自动洗牌（FR-6.2）

    @Test
    fun `消除后进入死局会自动洗牌且不消耗道具次数`() {
        // 2x3 棋盘：消掉竖直相邻的 C 之后，剩下的 A、B 两组各自对角、互相堵死
        val h = fixedHarness("CAB", "CBA")
        h.session.start()

        val shuffleLeftBefore = h.session.state.shuffleLeft
        assertFalse(
            "前置条件：开局不应是死局",
            !DeadlockDetector.hasMove(h.session.state.board),
        )

        h.session.select(at(0, 0))
        val result = h.session.select(at(1, 0))

        assertTrue(result is SelectResult.Eliminated)
        assertTrue("消除后应触发自动重排", (result as SelectResult.Eliminated).autoShuffled)
        assertEquals("剩余 4 张牌", 4, h.session.state.remainingTiles)
        assertEquals(
            "自动重排不得消耗洗牌道具次数（FR-6.2）",
            shuffleLeftBefore,
            h.session.state.shuffleLeft,
        )
        assertTrue(
            "重排后必须仍可继续游戏",
            DeadlockDetector.hasMove(h.session.state.board),
        )
    }

    // ============================================================ 提示（FR-9.1 / FR-9.4）

    @Test
    fun `提示高亮一对可连通的牌且不重置连击`() {
        val h = generatedHarness()
        h.session.start()

        // 先建立连击
        val first = DeadlockDetector.findMove(h.session.state.board)!!
        h.session.select(first.first.position)
        h.session.select(first.second.position)
        assertEquals(1, h.session.state.score.combo)

        val hint = h.session.useHint()
        assertTrue(hint is HintResult.Hinted)
        val hinted = hint as HintResult.Hinted

        assertEquals("使用提示不重置连击（FR-9.4）", 1, h.session.state.score.combo)
        assertEquals("应高亮两张牌", 2, h.session.state.hintedPositions.size)
        assertEquals(h.config.hintCount - 1, h.session.state.hintLeft)
        assertEquals(
            setOf(hinted.first, hinted.second),
            h.session.state.hintedPositions.toSet(),
        )
        assertNotNull(
            "被高亮的这一对必须真的可连通",
            PathFinder.find(h.session.state.board, hinted.first, hinted.second),
        )
    }

    @Test
    fun `玩家操作后提示高亮被清除`() {
        val h = generatedHarness()
        h.session.start()
        h.session.useHint()
        assertEquals(2, h.session.state.hintedPositions.size)

        val move = DeadlockDetector.findMove(h.session.state.board)!!
        h.session.select(move.first.position)

        assertTrue("玩家开始操作后不应保留高亮", h.session.state.hintedPositions.isEmpty())
    }

    @Test
    fun `提示次数用尽后不可再用`() {
        val h = generatedHarness()
        h.session.start()

        repeat(h.config.hintCount) {
            assertTrue(h.session.useHint() is HintResult.Hinted)
        }
        assertEquals(0, h.session.state.hintLeft)
        assertTrue(h.session.useHint() is HintResult.NoHintLeft)
        assertEquals("失败调用不应把次数变成负数", 0, h.session.state.hintLeft)
    }

    // ============================================================ 洗牌道具（FR-9.2 / FR-9.4）

    @Test
    fun `使用洗牌消耗次数并清空选中与连击`() {
        val h = generatedHarness()
        h.session.start()

        val move = DeadlockDetector.findMove(h.session.state.board)!!
        h.session.select(move.first.position)
        h.session.select(move.second.position)
        assertEquals(1, h.session.state.score.combo)
        val tilesBefore = h.session.state.board.remainingTiles().map { it.id }.toSet()

        h.session.select(DeadlockDetector.findMove(h.session.state.board)!!.first.position)
        assertNotNull("洗牌前应有选中态", h.session.state.selectedPosition)

        val result = h.session.useShuffle()

        assertTrue(result is ShuffleResult.Shuffled)
        assertEquals(h.config.shuffleCount - 1, (result as ShuffleResult.Shuffled).remaining)
        assertEquals(h.config.shuffleCount - 1, h.session.state.shuffleLeft)
        assertNull("洗牌应清空选中态（UC-05 步骤 4）", h.session.state.selectedPosition)
        assertEquals("洗牌应重置连击（FR-8.3 / FR-9.4）", 0, h.session.state.score.combo)
        assertEquals(
            "洗牌不得改变牌数与 id 集合",
            tilesBefore,
            h.session.state.board.remainingTiles().map { it.id }.toSet(),
        )
        assertTrue("洗牌后必须仍有解", DeadlockDetector.hasMove(h.session.state.board))
    }

    @Test
    fun `洗牌次数用尽后不可再用`() {
        val h = generatedHarness()
        h.session.start()

        repeat(h.config.shuffleCount) {
            assertTrue(h.session.useShuffle() is ShuffleResult.Shuffled)
        }
        assertEquals(0, h.session.state.shuffleLeft)
        assertTrue(h.session.useShuffle() is ShuffleResult.NoShuffleLeft)
        assertEquals("失败调用不应把次数变成负数", 0, h.session.state.shuffleLeft)
    }

    // ============================================================ 计时与暂停（FR-7）

    @Test
    fun `暂停期间倒计时停止且不能操作`() {
        val h = generatedHarness()
        h.session.start()

        h.clock.advance(5_000)
        h.session.tick()
        assertEquals(175, h.session.state.timeLeftSeconds)

        h.session.pause()
        assertEquals(GamePhase.PAUSED, h.session.state.phase)

        h.clock.advance(60_000)
        h.session.tick()
        assertEquals("暂停期间剩余时间不应变化", 175, h.session.state.timeLeftSeconds)
        assertEquals("暂停期间不应因超时判负", GamePhase.PAUSED, h.session.state.phase)

        assertTrue(h.session.select(at(0, 0)) is SelectResult.Ignored)
        assertTrue(h.session.useHint() is HintResult.NotAvailable)

        h.session.resume()
        assertEquals(GamePhase.PLAYING, h.session.state.phase)
        assertEquals(175, h.session.state.timeLeftSeconds)
    }

    @Test
    fun `时间耗尽判负并给出结算数据`() {
        val h = fixedHarness("AABB", "CCDD")
        h.session.start()

        h.session.select(at(0, 0))
        h.session.select(at(0, 1))
        val points = h.session.state.score.points
        assertEquals(10, points)

        h.clock.advance(h.config.timeLimitSeconds * 1000L)
        h.session.tick()

        assertEquals(GamePhase.LOSE, h.session.state.phase)
        val result = h.session.state.result
        assertNotNull("失败也应有结算数据", result)
        assertFalse(result!!.isWin)
        assertEquals(0, result.timeLeftSeconds)
        assertEquals("超时没有时间奖励", 0, result.timeBonus)
        assertEquals("失败时总分等于消除得分", points, result.score)
        assertEquals(1, result.maxCombo)
    }

    @Test
    fun `剩余时间低于 30 秒时置告警标记`() {
        val h = generatedHarness()
        h.session.start()
        assertFalse(h.session.state.isTimeLow)

        h.clock.advance((h.config.timeLimitSeconds - 30) * 1000L)
        h.session.tick()
        assertEquals(30, h.session.state.timeLeftSeconds)
        assertFalse("恰好 30 秒不算告警", h.session.state.isTimeLow)

        h.clock.advance(1_000)
        h.session.tick()
        assertTrue(h.session.state.isTimeLow)
    }

    // ============================================================ 重开（FR-9.5 / FR-10.7）

    @Test
    fun `重开会重置得分与道具次数`() {
        val h = generatedHarness()
        h.session.start()

        val move = DeadlockDetector.findMove(h.session.state.board)!!
        h.session.select(move.first.position)
        h.session.select(move.second.position)
        h.session.useHint()
        h.session.useShuffle()
        h.clock.advance(10_000)

        assertTrue(h.session.state.score.points > 0)
        assertTrue(h.session.state.hintLeft < h.config.hintCount)

        h.session.restart()

        assertEquals(GamePhase.PLAYING, h.session.state.phase)
        assertEquals(0, h.session.state.score.points)
        assertEquals(0, h.session.state.score.combo)
        assertEquals(h.config.hintCount, h.session.state.hintLeft)
        assertEquals(h.config.shuffleCount, h.session.state.shuffleLeft)
        assertEquals(h.config.timeLimitSeconds, h.session.state.timeLeftSeconds)
        assertEquals(h.config.tileCount, h.session.state.remainingTiles)
        assertNull(h.session.state.result)
    }

    // ============================================================ 完整一局（M5-8）

    @Test
    fun `自动求解完整一局并判定通关`() {
        val h = generatedHarness(LevelCatalog.configOf(1))
        h.session.start()

        var steps = 0
        while (!h.session.state.isFinished && steps < 500) {
            val move = DeadlockDetector.findMove(h.session.state.board)
            assertNotNull("第 $steps 步：棋盘上应始终存在可行步", move)

            assertEquals(SelectResult.Selected, h.session.select(move!!.first.position))
            val result = h.session.select(move.second.position)
            assertTrue("第 $steps 步应消除成功，实际为 $result", result is SelectResult.Eliminated)

            h.clock.advance(500)
            steps++
        }

        assertEquals("48 张牌应恰好 24 次消除", 24, steps)
        assertEquals(GamePhase.WIN, h.session.state.phase)
        assertTrue("棋盘应已清空", h.session.state.board.isCleared)
        assertEquals(0, h.session.state.remainingTiles)

        val result = h.session.state.result
        assertNotNull("通关应有结算数据", result)
        assertTrue(result!!.isWin)
        assertTrue("通关应发放时间奖励（FR-8.4）", result.timeBonus > 0)
        assertEquals(
            "总分 = 消除得分 + 时间奖励（SRS 9.2）",
            h.session.state.score.points + result.timeBonus,
            result.score,
        )
        assertTrue("全程连击应远超 1", result.maxCombo > 10)
    }

    @Test
    fun `清盘后不再响应操作`() {
        val h = generatedHarness(LevelCatalog.configOf(1))
        h.session.start()

        while (!h.session.state.isFinished) {
            val move = DeadlockDetector.findMove(h.session.state.board)!!
            h.session.select(move.first.position)
            h.session.select(move.second.position)
            h.clock.advance(100)
        }

        assertEquals(GamePhase.WIN, h.session.state.phase)
        assertTrue(h.session.select(at(0, 0)) is SelectResult.Ignored)
        assertTrue(h.session.useHint() is HintResult.NotAvailable)
        assertTrue(h.session.useShuffle() is ShuffleResult.NotAvailable)
    }

    @Test
    fun `每一关都能自动求解通关`() {
        for (config in LevelCatalog.levels) {
            val h = generatedHarness(config)
            h.session.start()

            var steps = 0
            val limit = config.tileCount / 2 + 5
            while (!h.session.state.isFinished && steps <= limit) {
                val move = DeadlockDetector.findMove(h.session.state.board)
                assertNotNull("第 ${config.level} 关中途出现无解且未自动重排", move)
                h.session.select(move!!.first.position)
                h.session.select(move.second.position)
                h.clock.advance(200)
                steps++
            }

            assertEquals("第 ${config.level} 关应恰好 ${config.tileCount / 2} 步清盘", config.tileCount / 2, steps)
            assertEquals("第 ${config.level} 关应通关", GamePhase.WIN, h.session.state.phase)
        }
    }
}
