package com.ldxy.lianliankan.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.ldxy.lianliankan.data.DataStoreKeys
import com.ldxy.lianliankan.data.DataStoreSettingsRepository
import com.ldxy.lianliankan.data.InMemorySettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动画面的放行门控与「设置已加载」信号（V1.4 需求 ②）。
 *
 * 这是 V1.4 里**唯一一条高风险项**（计划 R-3）的回归测试：
 * 放行条件若永不为真，应用就再也起不来。把逻辑提成 [SplashGate] 之后，
 * 「正常路径」与「超时兜底」两条分支都能在这里用虚拟时间钉死 ——
 * 放在 `MainActivity` 里就只能靠真机验收了。
 *
 * 假 DataStore 让「读不出来」变成可构造的场景：真实设备上它需要文件损坏才会发生。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashGateTest {

    // ============================================================ 内存实现

    @Test
    fun `内存设置仓库构造完成即可放行`() = runTest {
        val repository = InMemorySettingsRepository()

        assertTrue("内存实现没有读盘这一步，应立即就绪", repository.isLoaded.value)
        assertEquals(
            "放行时应同时拿到设置本身，供首次组合使用",
            repository.settings.value,
            SplashGate.awaitSettings(repository),
        )
    }

    // ============================================================ DataStore 实现

    @Test
    fun `DataStore 设置仓库读到首值前不放行`() = runTest {
        val repository = DataStoreSettingsRepository(FakePreferencesDataStore(), backgroundScope)

        assertFalse("首个值还没读出来，启动画面必须继续按住", repository.isLoaded.value)

        // 用**前台**等待推动调度器：advanceUntilIdle 只保证前台任务跑完，
        // 后台预热协程要靠前台挂起时才被调度到。
        repository.isLoaded.first { it }

        assertTrue("读到首个值后应立即放行", repository.isLoaded.value)
    }

    @Test
    fun `读到设置时放行并返回玩家实际选的那一份`() = runTest {
        val store = FakePreferencesDataStore(
            preferencesOf(
                DataStoreKeys.THEME_PALETTE_ID to 2,
                DataStoreKeys.SKIN_ID to 1,
            ),
        )
        val repository = DataStoreSettingsRepository(store, backgroundScope)

        val settings = SplashGate.awaitSettings(repository)

        // 这正是需求 ② 的第二个成因：拿到真实值再放行，首次组合就不会先渲染
        // 默认的浅蓝配色、再跳到玩家选的玫红。
        assertEquals(2, settings?.themePaletteId)
        assertEquals(1, settings?.skinId)
    }

    // ============================================================ 超时兜底（R-3）

    @Test
    fun `读不出设置时信号保持 false - 这就是需要超时兜底的理由`() = runTest {
        val store = SilentPreferencesDataStore()
        val repository = DataStoreSettingsRepository(store, backgroundScope)

        // 先等到预热协程真的订阅上、并卡在那里，否则下面的断言是空的：
        // 它可能只是「协程还没被调度」，而不是「读不出来」。
        store.subscribers.first { it > 0 }

        assertFalse(
            "DataStore 读不出来时 isLoaded 不会自己变 true；" +
                "若放行条件只看它，应用就永远起不来了",
            repository.isLoaded.value,
        )
    }

    @Test
    fun `读不出设置时超时放行而不是永久挂起`() = runTest {
        val store = SilentPreferencesDataStore()
        val repository = DataStoreSettingsRepository(store, backgroundScope)
        store.subscribers.first { it > 0 }

        // runTest 的虚拟时间：这里的 1.5s 不会真的等待
        val settings = SplashGate.awaitSettings(repository)

        assertNull("超时路径应返回 null，由调用方退回默认设置并照常放行", settings)
    }

    // ============================================================ 假 DataStore

    /** 可读可写的假 DataStore：首个值立即就绪，用来覆盖正常路径。 */
    private class FakePreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {

        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    /**
     * 永不发射、也永不结束的假 DataStore —— 也就是「读盘卡住」。
     *
     * 不能用 `emptyFlow()`：那是**立即结束**，`first()` 会抛异常，
     * 与真实世界里「一直读不出来」不是一回事。
     */
    private class SilentPreferencesDataStore : DataStore<Preferences> {

        private val flow = MutableSharedFlow<Preferences>()

        /** 当前订阅者数量，供测试确认「预热协程确实卡在读取上」。 */
        val subscribers: StateFlow<Int> = flow.subscriptionCount

        override val data: Flow<Preferences> = flow

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = throw UnsupportedOperationException("这个假实现只用于验证「读不出来」")
    }
}
