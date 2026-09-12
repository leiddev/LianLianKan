package com.ldxy.lianliankan.domain.score

/**
 * 关卡倒计时（SRS FR-7）。
 *
 * 时间源通过 [clock] 注入，默认使用**单调时钟**（`System.nanoTime`），
 * 以免受系统时间被调整的影响（SRS 5.2）。测试可注入假时钟，完全确定。
 *
 * 暂停语义（SRS FR-7.3）：[pause] 把已流逝时间累加进 [accumulatedMillis] 并停止计时，
 * [resume] 从当前时刻继续累计。暂停期间反复调用 [elapsedMillis] 返回值不变。
 */
class GameTimer(
    val limitSeconds: Int,
    private val clock: () -> Long = MONOTONIC_CLOCK,
) {

    init {
        require(limitSeconds > 0) { "限时必须为正，实际为 $limitSeconds" }
    }

    /** 已结束的计时区间累计时长（毫秒）。 */
    private var accumulatedMillis: Long = 0L

    /** 当前计时区间的起点；为 `null` 表示计时已停止。 */
    private var runningSince: Long? = null

    /** 是否正在计时。 */
    val isRunning: Boolean get() = runningSince != null

    /** 从开始到现在的有效计时时长（毫秒），不含暂停区间。 */
    fun elapsedMillis(): Long =
        accumulatedMillis + (runningSince?.let { clock() - it } ?: 0L)

    /** 限时总毫秒数。 */
    val limitMillis: Long get() = limitSeconds * 1000L

    /** 剩余毫秒数，不小于 0。 */
    fun remainingMillis(): Long = (limitMillis - elapsedMillis()).coerceAtLeast(0L)

    /**
     * 剩余秒数，向上取整，用于倒计时展示（SRS FR-7.1）。
     *
     * 刚开始时等于 [limitSeconds]，恰好耗尽时为 0。
     */
    fun remainingSeconds(): Int {
        val millis = remainingMillis()
        return ((millis + 999L) / 1000L).toInt()
    }

    /** 时间是否已耗尽（SRS FR-7.2）。 */
    val isExpired: Boolean get() = elapsedMillis() >= limitMillis

    /** 剩余时间是否已进入告警区间（SRS FR-7.4：低于 [LOW_TIME_THRESHOLD_SECONDS] 秒）。 */
    val isTimeLow: Boolean get() = remainingSeconds() < LOW_TIME_THRESHOLD_SECONDS

    /** 开始计时，并把已流逝时间清零（重开本关时使用）。 */
    fun start() {
        accumulatedMillis = 0L
        runningSince = clock()
    }

    /** 暂停计时。重复调用无副作用（SRS FR-7.3）。 */
    fun pause() {
        val since = runningSince ?: return
        accumulatedMillis += clock() - since
        runningSince = null
    }

    /** 继续计时。未开始时调用等同于 [start]；已在计时时重复调用无副作用。 */
    fun resume() {
        if (runningSince != null) return
        runningSince = clock()
    }

    companion object {
        /** SRS FR-7.4：剩余时间低于该值触发视觉提示。 */
        const val LOW_TIME_THRESHOLD_SECONDS: Int = 30

        /**
         * 默认时间源：单调时钟（SRS 5.2）。
         *
         * `nanoTime` 的起止点任意但恒定单调，仅用于测量时间差，不受系统时间调整影响。
         */
        val MONOTONIC_CLOCK: () -> Long = { System.nanoTime() / 1_000_000L }
    }
}
