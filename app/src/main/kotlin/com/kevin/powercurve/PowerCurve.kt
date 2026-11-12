package com.kevin.powercurve

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Tracks the best rolling-average power for each duration over the current ride.
 * Prefix sums retain active power history so new intervals can be calculated mid-ride.
 * Updating each configured interval is O(1) per sample.
 */
class PowerCurve {

    private var intervals = PowerCurveConfig.defaults
    private var prefix = LongArray(3601)
    private var samples = 0

    data class Point(val seconds: Long, val watts: Int)

    data class Snapshot(
        val best: List<Int?>,
        val activeSeconds: Long = 0,
        val rideAverage: Int? = null,
        val intervals: List<PowerCurveConfig.Interval> = PowerCurveConfig.defaults,
    ) {
        init {
            require(best.size == intervals.size)
        }
        /** The full-ride average is the only rolling average covering all active seconds. */
        val points: List<Point>
            get() = best.mapIndexedNotNull { i, watts ->
                if (watts != null && intervals[i].seconds < activeSeconds) {
                    Point(intervals[i].seconds.toLong(), watts)
                } else {
                    null
                }
            } + if (activeSeconds > 0 && rideAverage != null) {
                listOf(Point(activeSeconds, rideAverage))
            } else {
                emptyList()
            }
    }

    private val _snapshot = MutableStateFlow(Snapshot(emptyCurve()))
    val snapshot: StateFlow<Snapshot> = _snapshot

    @Synchronized
    fun addSample(watts: Int) {
        require(watts >= 0)
        if (samples + 1 == prefix.size) prefix = prefix.copyOf(prefix.size * 2)
        prefix[samples + 1] = prefix[samples] + watts
        samples++

        val current = _snapshot.value.best
        val updated = intervals.mapIndexed { i, interval ->
            if (samples >= interval.seconds) {
                maxOf(current[i] ?: 0, average(samples, interval.seconds))
            } else {
                current[i]
            }
        }
        publish(updated)
    }

    @Synchronized
    fun configure(updated: List<PowerCurveConfig.Interval>) {
        require(updated.isNotEmpty() && updated.all { it.seconds > 0 } &&
            updated.zipWithNext().all { (a, b) -> a.seconds < b.seconds })
        if (intervals == updated) return
        intervals = updated.toList()
        val best = intervals.map { interval ->
            if (samples < interval.seconds) null else {
                var maximum = 0
                for (end in interval.seconds..samples) {
                    maximum = maxOf(maximum, average(end, interval.seconds))
                }
                maximum
            }
        }
        publish(best)
    }

    @Synchronized
    fun reset() {
        prefix = LongArray(3601)
        samples = 0
        publish(emptyCurve())
    }

    private fun average(end: Int, duration: Int): Int =
        ((prefix[end] - prefix[end - duration]).toDouble() / duration).roundToInt()

    private fun publish(best: List<Int?>) {
        _snapshot.value = Snapshot(best, samples.toLong(),
            if (samples > 0) average(samples, samples) else null, intervals)
    }

    private fun emptyCurve(): List<Int?> = List(intervals.size) { null }

    companion object {
        val live = PowerCurve()

        fun durationLabel(seconds: Long): String = buildString {
            val hours = seconds / 3600
            val minutes = seconds % 3600 / 60
            val remainder = seconds % 60
            if (hours > 0) append("${hours}h")
            if (minutes > 0) append("${minutes}m")
            if (remainder > 0 || isEmpty()) append("${remainder}s")
        }
    }
}
