package com.kevin.powercurve

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

object PowerCurveConfig {
    data class Interval(
        val label: String,
        val seconds: Int,
    )

    val defaults = listOf(
        Interval("15s", 15),
        Interval("30s", 30),
        Interval("60s", 60),
        Interval("2m", 120),
        Interval("5m", 300),
        Interval("8m", 480),
        Interval("20m", 1200),
        Interval("60m", 3600),
    )

    data class Settings(val intervals: List<Interval> = defaults, val smooth: Boolean = true)

    private const val PREFS = "power_curve_settings"
    private var loaded = false
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    fun parse(text: String): List<Interval> {
        require(text.isNotBlank()) { "Enter at least one interval." }
        val intervals = text.split(",").map { entry ->
            val match = Regex("""^\s*(\d+)\s*(s|sec|m|min|h|hr)\s*$""", RegexOption.IGNORE_CASE)
                .matchEntire(entry)
            require(match != null) { "Use whole numbers with s, min or h, separated by commas." }
            val amount = match.groupValues[1].toLongOrNull()
            val unit = match.groupValues[2].lowercase()
            val multiplier = when (unit) {
                "s", "sec" -> 1
                "m", "min" -> 60
                else -> 3600
            }
            require(amount != null && amount in 1..(Int.MAX_VALUE / multiplier).toLong()) {
                "Intervals must be positive and no longer than ${Int.MAX_VALUE} seconds."
            }
            Interval("$amount${when (multiplier) { 1 -> "s"; 60 -> "m"; else -> "h" }}",
                (amount * multiplier).toInt())
        }.sortedBy { it.seconds }
        require(intervals.map { it.seconds }.distinct().size == intervals.size) {
            "Each interval must be unique (for example, 60s and 1min are the same)."
        }
        return intervals
    }

    fun format(intervals: List<Interval>): String = intervals.joinToString(", ") { it.label }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val intervals = try {
            parse(prefs.getString("intervals", format(defaults)) ?: format(defaults))
        } catch (error: IllegalArgumentException) {
            Log.e("PowerCurve", "Invalid saved intervals; loading defaults", error)
            Toast.makeText(context, R.string.settings_invalid_saved, Toast.LENGTH_LONG).show()
            defaults
        }
        val saved = Settings(intervals, prefs.getBoolean("smooth", true))
        PowerCurve.live.configure(saved.intervals)
        _settings.value = saved
        loaded = true
    }

    @Synchronized
    fun save(context: Context, settings: Settings) {
        val normalized = settings.copy(intervals = parse(format(settings.intervals)))
        val success = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("intervals", format(normalized.intervals))
            .putBoolean("smooth", normalized.smooth)
            .commit()
        if (!success) throw IOException("Unable to persist Power Curve settings")
        PowerCurve.live.configure(normalized.intervals)
        PowerCurveViewState.selectFirst(context, "power-curve-graph")
        _settings.value = normalized
        loaded = true
    }
}
