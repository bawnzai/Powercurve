package com.kevin.powercurve

import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PowerCurveExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val consumerIds = mutableListOf<String>()

    @Volatile private var currentPower = 0
    @Volatile private var rideState: RideState = RideState.Idle

    override val types by lazy {
        listOf(
            PowerCurveDataType(EXTENSION_ID, "power-curve-graph"),
        )
    }

    override fun onCreate() {
        super.onCreate()
        PowerCurveConfig.load(applicationContext)
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { connected ->
            if (connected && consumerIds.isEmpty()) registerConsumers()
        }
        startSampler()
    }

    private fun registerConsumers() {
        consumerIds += karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.POWER),
        ) { event ->
            val watts = (event.state as? StreamState.Streaming)?.dataPoint?.singleValue
            // Sensor dropout / searching counts as 0 W
            currentPower = watts?.roundToInt()?.coerceAtLeast(0) ?: 0
        }
        consumerIds += karooSystem.addConsumer<RideState> { state ->
            if (rideState is RideState.Idle && state is RideState.Recording) PowerCurve.live.reset()
            rideState = state
        }
    }

    /** Takes one power sample per second while the ride is recording (paused time is excluded). */
    private fun startSampler() {
        scope.launch {
            var next = SystemClock.elapsedRealtime()
            while (isActive) {
                next += 1000
                val now = SystemClock.elapsedRealtime()
                // Don't burst-fill samples after a long stall (e.g. device sleep)
                if (now - next > 5000) next = now
                delay((next - now).coerceAtLeast(0))
                if (rideState is RideState.Recording) PowerCurve.live.addSample(currentPower)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        consumerIds.forEach { karooSystem.removeConsumer(it) }
        consumerIds.clear()
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTENSION_ID = "power-curve"
    }
}
