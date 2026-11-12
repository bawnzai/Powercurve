package com.kevin.powercurve

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PowerCurveIntervalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CYCLE_INTERVAL) return
        val typeId = intent.getStringExtra(EXTRA_TYPE_ID) ?: return
        val count = intent.getIntExtra(EXTRA_INTERVAL_COUNT, 0)
        if (count < 2) {
            Log.e("PowerCurve", "Interval tap missing a valid interval count")
            return
        }
        PowerCurveViewState.ensureLoaded(context, typeId, count)
        PowerCurveViewState.cycle(context, typeId, count)
    }

    companion object {
        const val ACTION_CYCLE_INTERVAL = "com.kevin.powercurve.ACTION_CYCLE_INTERVAL"
        const val EXTRA_TYPE_ID = "extra_type_id"
        const val EXTRA_INTERVAL_COUNT = "extra_interval_count"
    }
}
