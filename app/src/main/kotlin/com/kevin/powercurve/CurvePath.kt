package com.kevin.powercurve

import android.graphics.Path
import android.graphics.PointF

internal object CurvePath {
    fun create(points: List<PointF>, smooth: Boolean): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        val slopes = points.zipWithNext { a, b ->
            if (b.x > a.x) (b.y - a.y) / (b.x - a.x) else 0f
        }
        val tangents = FloatArray(points.size) { i ->
            when {
                slopes.isEmpty() -> 0f
                i == 0 -> slopes.first()
                i == points.lastIndex -> slopes.last()
                slopes[i - 1] * slopes[i] <= 0f -> 0f
                else -> 2f * slopes[i - 1] * slopes[i] / (slopes[i - 1] + slopes[i])
            }
        }
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            if (smooth && b.x > a.x) {
                // Harmonic-mean tangents preserve shape without overshooting adjacent samples.
                val third = (b.x - a.x) / 3f
                path.cubicTo(a.x + third, a.y + tangents[i] * third,
                    b.x - third, b.y - tangents[i + 1] * third, b.x, b.y)
            } else {
                path.lineTo(b.x, b.y)
            }
        }
        return path
    }
}
