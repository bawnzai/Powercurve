package com.kevin.powercurve

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PowerCurveSettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerCurveConfig.load(this)
        setContentView(R.layout.power_curve_settings)
        val intervals = findViewById<EditText>(R.id.settings_intervals)
        val smooth = findViewById<Switch>(R.id.settings_smooth)
        val status = findViewById<TextView>(R.id.settings_status)
        val save = findViewById<Button>(R.id.settings_save)
        val defaults = findViewById<Button>(R.id.settings_defaults)
        val settings = PowerCurveConfig.settings.value
        if (savedInstanceState == null) {
            intervals.setText(PowerCurveConfig.format(settings.intervals))
            smooth.isChecked = settings.smooth
        }
        defaults.setOnClickListener {
            intervals.setText(PowerCurveConfig.format(PowerCurveConfig.defaults))
            smooth.isChecked = true
            status.setText(R.string.settings_defaults_hint)
        }
        save.setOnClickListener {
            val parsed = try {
                PowerCurveConfig.parse(intervals.text.toString())
            } catch (error: IllegalArgumentException) {
                intervals.error = error.message
                return@setOnClickListener
            }
            val updated = PowerCurveConfig.Settings(parsed, smooth.isChecked)
            intervals.error = null
            save.isEnabled = false
            defaults.isEnabled = false
            status.setText(R.string.settings_saving)
            scope.launch {
                try {
                    withContext(Dispatchers.Default) { PowerCurveConfig.save(applicationContext, updated) }
                    intervals.setText(PowerCurveConfig.format(parsed))
                    status.setText(R.string.settings_saved)
                } catch (error: IOException) {
                    Log.e("PowerCurve", "Could not save settings", error)
                    status.setText(R.string.settings_save_error)
                } finally {
                    save.isEnabled = true
                    defaults.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
