package com.kevin.powercurve

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.karooext.models.UpdateGraphicConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.pow

class PowerCurveDataType(
    extension: String,
    typeId: String,
) : DataTypeImpl(extension, typeId) {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        PowerCurveConfig.load(context)
        PowerCurveViewState.ensureLoaded(context, typeId, PowerCurveConfig.settings.value.intervals.size + 1)
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val job = CoroutineScope(Dispatchers.Default).launch {
            combine(
                if (config.preview) PowerCurveConfig.settings.map { preview(it.intervals) } else PowerCurve.live.snapshot,
                PowerCurveViewState.selectedIndexFlow(typeId).distinctUntilChanged(),
                PowerCurveConfig.settings,
            ) { best, selectedIndex, settings -> Triple(best, selectedIndex, settings.smooth) }
                .conflate()
                .collect { (best, selectedIndex, smooth) ->
                    emitter.updateView(buildView(context, config, best,
                        selectedIndex.coerceIn(0, best.intervals.size), smooth))
                    // Karoo drops views sent faster than 1 Hz; retain the latest pending tap.
                    delay(1100)
                }
        }
        emitter.setCancellable { job.cancel() }
    }

    internal fun buildView(
        context: Context,
        config: ViewConfig,
        snapshot: PowerCurve.Snapshot,
        selectedIndex: Int,
        smooth: Boolean = true,
    ): RemoteViews {
        val root = RemoteViews(context.packageName, R.layout.power_curve_view)
        val isRide = selectedIndex == snapshot.intervals.size
        val interval = if (isRide) {
            context.getString(R.string.ride_interval, PowerCurve.durationLabel(snapshot.activeSeconds))
        } else {
            snapshot.intervals[selectedIndex].label
        }
        val selectedLabel = if (config.preview) context.getString(R.string.preview_interval, interval) else interval
        val selectedValue = if (isRide) snapshot.rideAverage else snapshot.best[selectedIndex]

        val density = context.resources.displayMetrics.density
        val contentWidth = config.viewSize.first.toFloat()
        val contentHeight = (config.viewSize.second - 12 * density).coerceAtLeast(3f)
        val headerSize = min(28 * density, contentHeight * 0.20f)
        val graphWidth = config.viewSize.first.coerceAtLeast(1)
        val graphHeight = (contentHeight - headerSize * 1.3f).roundToInt().coerceAtLeast(1)
        val graphBitmap = drawGraph(context, snapshot, selectedIndex, graphWidth, graphHeight, smooth)
        val value = selectedValue?.let { "$it W" } ?: "--"
        val textPaint = Paint().apply { typeface = Typeface.DEFAULT_BOLD }
        fun fittedTextSize(text: String, preferredSize: Float): Float {
            textPaint.textSize = preferredSize
            return preferredSize * min(1f, (contentWidth / 2 - 8 * density).coerceAtLeast(1f) / textPaint.measureText(text))
        }

        root.setImageViewBitmap(R.id.graph, graphBitmap)
        root.setTextViewText(R.id.interval_label, selectedLabel)
        root.setTextViewText(R.id.interval_value, value)
        root.setTextViewTextSize(R.id.interval_label, TypedValue.COMPLEX_UNIT_PX,
            fittedTextSize(selectedLabel, headerSize))
        root.setTextViewTextSize(R.id.interval_value, TypedValue.COMPLEX_UNIT_PX,
            fittedTextSize(value, headerSize))

        val intent = Intent(context, PowerCurveIntervalReceiver::class.java).apply {
            action = PowerCurveIntervalReceiver.ACTION_CYCLE_INTERVAL
            putExtra(PowerCurveIntervalReceiver.EXTRA_TYPE_ID, typeId)
            putExtra(PowerCurveIntervalReceiver.EXTRA_INTERVAL_COUNT, snapshot.intervals.size + 1)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            dataTypeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        root.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        return root
    }

    private fun drawGraph(
        context: Context,
        snapshot: PowerCurve.Snapshot,
        selectedIndex: Int,
        width: Int,
        height: Int,
        smooth: Boolean,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(context, R.color.graph_background)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val scale = min(width / 240f, height / 120f).coerceIn(0.25f, 1.5f)
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(context, R.color.text_secondary)
            textSize = 12f * scale
        }
        val chartLeft = 38f * scale
        val chartTop = 12f * scale
        val chartRight = width - 12f * scale
        val chartBottom = height - 22f * scale
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val values = snapshot.points
        val maxY = ((values.maxOfOrNull { it.watts } ?: 100) * 1.1f).coerceAtLeast(1f)
        val gridPaint = Paint().apply {
            color = color(context, R.color.text_secondary)
            alpha = 60
            strokeWidth = scale
        }
        for (step in 0..2) {
            val y = chartBottom - chartHeight * step / 2
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText((maxY * step / 2).roundToInt().toString(), 2f * scale, y, axisPaint)
        }
        val minDuration = values.firstOrNull()?.seconds ?: snapshot.intervals.first().seconds.toLong()
        val maxDuration = values.lastOrNull()?.seconds ?: snapshot.intervals.last().seconds.toLong()
        if (minDuration != maxDuration) {
            canvas.drawText(PowerCurve.durationLabel(minDuration), chartLeft, height - 4f * scale, axisPaint)
        }
        axisPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(PowerCurve.durationLabel(maxDuration), chartRight, height - 4f * scale, axisPaint)
        if (values.isEmpty()) {
            axisPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.graph_waiting), (chartLeft + chartRight) / 2,
                (chartTop + chartBottom) / 2, axisPaint)
            return bitmap
        }

        val logMin = ln(minDuration.toDouble())
        val logRange = ln(maxDuration.toDouble()) - logMin
        fun x(seconds: Long): Float = if (logRange == 0.0) chartRight else
            chartLeft + ((ln(seconds.toDouble()) - logMin) / logRange).toFloat() * chartWidth
        fun y(watts: Int): Float = chartBottom - watts / maxY * chartHeight

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            shader = LinearGradient(
                chartLeft,
                chartTop,
                chartRight,
                chartBottom,
                color(context, R.color.graph_line_start),
                color(context, R.color.graph_line_end),
                Shader.TileMode.CLAMP,
            )
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = color(context, R.color.graph_point)
        }
        val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = color(context, R.color.graph_selected_point)
        }

        val path = CurvePath.create(values.map { PointF(x(it.seconds), y(it.watts)) }, smooth)
        val area = Path(path).apply {
            lineTo(x(values.last().seconds), chartBottom)
            lineTo(x(values.first().seconds), chartBottom)
            close()
        }
        val fillColor = color(context, R.color.graph_line_end)
        fun fillAlpha(alpha: Int): Int = Color.argb(alpha,
            Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, chartTop, 0f, chartBottom,
                fillAlpha(150), fillAlpha(35), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(path, linePaint)
        values.forEach { point -> canvas.drawCircle(x(point.seconds), y(point.watts), 3f * scale, pointPaint) }

        val selectedSeconds = if (selectedIndex == snapshot.intervals.size) snapshot.activeSeconds else
            snapshot.intervals[selectedIndex].seconds.toLong()
        values.firstOrNull { it.seconds == selectedSeconds }?.let { point ->
            canvas.drawCircle(x(point.seconds), y(point.watts), 6f * scale, selectedPaint)
        }

        return bitmap
    }

    private fun color(context: Context, id: Int): Int = context.getColor(id)

    companion object {
        internal val PREVIEW = preview(PowerCurveConfig.defaults)

        internal fun preview(intervals: List<PowerCurveConfig.Interval>): PowerCurve.Snapshot {
            val seconds = maxOf(14400L, intervals.last().seconds.toLong())
            return PowerCurve.Snapshot(
                best = intervals.map {
                    if (it.seconds.toLong() == seconds) 210 else
                        (210 + 710 * (15.0 / it.seconds).pow(0.65)).roundToInt()
                },
                activeSeconds = seconds,
                rideAverage = 210,
                intervals = intervals,
            )
        }
    }
}
