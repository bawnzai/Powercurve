package com.kevin.powercurve

import android.graphics.PathMeasure
import android.graphics.PointF
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.abs

class PowerCurveSettingsTest {
    @Test
    fun settingsLaunchValidateSaveAndSurviveRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PowerCurveConfig.load(context)
        val original = PowerCurveConfig.settings.value
        assertNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        try {
            ActivityScenario.launch(PowerCurveSettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val input = activity.findViewById<EditText>(R.id.settings_intervals)
                    input.setText("0s")
                    activity.findViewById<Button>(R.id.settings_save).performClick()
                    assertNotNull(input.error)
                    input.setText("45s, 2min")
                    activity.findViewById<Switch>(R.id.settings_smooth).isChecked = false
                    activity.findViewById<Button>(R.id.settings_save).performClick()
                }
                val expected = PowerCurveConfig.Settings(PowerCurveConfig.parse("45s, 2min"), false)
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (PowerCurveConfig.settings.value != expected && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(25)
                }
                assertEquals(expected, PowerCurveConfig.settings.value)
                assertEquals(expected.intervals, PowerCurve.live.snapshot.value.intervals)
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals("45s, 2m",
                        activity.findViewById<EditText>(R.id.settings_intervals).text.toString())
                    assertFalse(activity.findViewById<Switch>(R.id.settings_smooth).isChecked)
                }
                val prefs = context.getSharedPreferences("power_curve_settings", 0)
                assertEquals("45s, 2m", prefs.getString("intervals", null))
                assertFalse(prefs.getBoolean("smooth", true))
            }
        } finally {
            PowerCurveConfig.save(context, original)
        }
    }

    @Test
    fun intervalsAcceptUnitsSortAndRoundTrip() {
        val parsed = PowerCurveConfig.parse("2H, 30s, 8min, 1sec")
        assertEquals(listOf(1, 30, 480, 7200), parsed.map { it.seconds })
        assertEquals(parsed, PowerCurveConfig.parse(PowerCurveConfig.format(parsed)))
        for (invalid in listOf("", "0s", "-1min", "5", "1.5min", "1s,", "60s,1min", "9999999999999999h")) {
            assertThrows(IllegalArgumentException::class.java) { PowerCurveConfig.parse(invalid) }
        }
    }

    @Test
    fun smoothPathPassesThroughSamplesWithoutOvershooting() {
        for (points in listOf(
            listOf(PointF(0f, 0f), PointF(10f, 90f), PointF(200f, 95f), PointF(220f, 100f)),
            listOf(PointF(0f, 100f), PointF(10f, 0f), PointF(200f, 95f), PointF(220f, 95f)),
        )) {
            val curved = PathMeasure(CurvePath.create(points, true), false)
            val straight = PathMeasure(CurvePath.create(points, false), false)
            assertTrue(abs(curved.length - straight.length) > 0.1f)
            val position = FloatArray(2)
            val nearest = FloatArray(points.size) { Float.MAX_VALUE }
            for (step in 0..10000) {
                curved.getPosTan(curved.length * step / 10000, position, null)
                assertFalse(position.any { !it.isFinite() })
                val segment = points.zipWithNext().firstOrNull {
                    position[0] >= it.first.x - 0.01f && position[0] <= it.second.x + 0.01f
                }
                assertTrue(segment != null)
                if (segment != null) {
                    assertTrue(position[1] >= minOf(segment.first.y, segment.second.y) - 0.05f)
                    assertTrue(position[1] <= maxOf(segment.first.y, segment.second.y) + 0.05f)
                }
                points.forEachIndexed { i, point ->
                    nearest[i] = minOf(nearest[i], abs(position[0] - point.x) + abs(position[1] - point.y))
                }
            }
            assertTrue(nearest.all { it < 0.1f })
        }
        assertTrue(CurvePath.create(emptyList(), true).isEmpty)
        assertEquals(0f, PathMeasure(CurvePath.create(listOf(PointF(1f, 1f)), true), false).length)
    }
}
