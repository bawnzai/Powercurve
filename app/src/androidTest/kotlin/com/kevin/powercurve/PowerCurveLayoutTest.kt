package com.kevin.powercurve

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerCurveLayoutTest {
    @Test
    fun graphAndValuesFitEveryIntervalAtLargeAndSmallFieldSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val type = PowerCurveDataType("power-curve", "power-curve-graph")
            for ((width, height) in listOf(478 to 594, 478 to 200, 238 to 160, 238 to 100)) {
                for (selected in 0..PowerCurveDataType.PREVIEW.intervals.size) {
                    val config = ViewConfig(60 to 60, width to height, 200, preview = true)
                    val remote = type.buildView(context, config, PowerCurveDataType.PREVIEW, selected)
                    val view = remote.apply(context, FrameLayout(context))
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    view.layout(0, 0, width, height)
                    val graph = view.findViewById<ImageView>(R.id.graph)
                    assertEquals("Graph must span the full field width", width, graph.width)
                    assertTrue("Graph must occupy at least half of ${width}x$height, actual ${graph.height}",
                        graph.height >= height / 2)
                    val header = view.findViewById<View>(R.id.graph_header)
                    assertTrue("Interval and watts must be above the graph", header.bottom <= graph.top)
                    for (id in listOf(R.id.interval_label, R.id.interval_value)) {
                        val text = view.findViewById<TextView>(id)
                        assertTrue("Text must fit horizontally", text.paint.measureText(text.text.toString()) <= text.width)
                        assertTrue("Text must fit vertically at ${width}x$height: ${text.text}",
                            text.height >= text.layout.height)
                    }
                    val bitmap = Bitmap.createBitmap(graph.width, graph.height, Bitmap.Config.ARGB_8888)
                    graph.draw(Canvas(bitmap))
                    var coloredPixels = 0
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) {
                            val pixel = bitmap.getPixel(x, y)
                            if (Color.green(pixel) - Color.red(pixel) > 40 ||
                                Color.red(pixel) - Color.blue(pixel) > 80) coloredPixels++
                        }
                    }
                    assertTrue("Colored curve and selection must actually render", coloredPixels > 20)
                    val background = context.getColor(R.color.graph_background)
                    assertEquals("Space above the curve must remain unshaded", background,
                        bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 5))
                    assertNotEquals("Area below the curve must be shaded", background,
                        bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height * 4 / 5))
                    if (selected == PowerCurveDataType.PREVIEW.intervals.size) {
                        assertTrue(view.findViewById<TextView>(R.id.interval_label).text.contains("4h"))
                        assertEquals("210 W", view.findViewById<TextView>(R.id.interval_value).text.toString())
                        var endpointVisible = false
                        for (x in bitmap.width - 25 until bitmap.width) {
                            for (y in 0 until bitmap.height) {
                                val pixel = bitmap.getPixel(x, y)
                                if (Color.red(pixel) - Color.blue(pixel) > 80) endpointVisible = true
                            }
                        }
                        assertTrue("Full-ride point must be at the right edge, not clipped", endpointVisible)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
