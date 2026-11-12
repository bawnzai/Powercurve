package com.kevin.powercurve

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object PowerCurveViewState {
    private const val PREFS_NAME = "power_curve_widget_state"
    private const val KEY_PREFIX = "selected_interval_"

    private val selectedIndices = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun ensureLoaded(context: Context, typeId: String, max: Int) {
        if (selectedIndices.value.containsKey(typeId)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_PREFIX + typeId, 0).coerceIn(0, max - 1)
        selectedIndices.update { it + (typeId to saved) }
    }

    fun selectedIndexFlow(typeId: String) = selectedIndices.map { it[typeId] ?: 0 }

    fun selectFirst(context: Context, typeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PREFIX + typeId, 0).apply()
        selectedIndices.update { it + (typeId to 0) }
    }

    fun cycle(context: Context, typeId: String, max: Int) {
        val current = selectedIndices.value[typeId] ?: 0
        val next = (current + 1) % max
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PREFIX + typeId, next)
            .apply()
        selectedIndices.update { it + (typeId to next) }
    }
}
