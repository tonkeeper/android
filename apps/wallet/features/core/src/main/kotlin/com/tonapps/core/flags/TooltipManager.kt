package com.tonapps.core.flags

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

@SuppressLint("StaticFieldLeak")
object TooltipManager {

    private const val PREFERENCES_NAME = "AppTooltipManager"

    private lateinit var context: Context

    private val prefs by lazy {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }

    fun initialize(context: Context) {
        this.context = context
    }

    fun setState(tooltip: TooltipKey, state: TooltipState) {
        prefs.edit {
            putString(tooltip.tooltipName, state.name)
        }
        if (state == TooltipState.NOT_SHOWN) {
            resetShowCount(tooltip)
            resetSession(tooltip)
        }
    }

    fun reset(tooltip: TooltipKey) {
        prefs.edit {
            remove(tooltip.tooltipName)
        }
    }

    fun getState(tooltip: TooltipKey): TooltipState {
        val stored = prefs.getString(tooltip.tooltipName, null)
        return stored?.let { TooltipState.valueOf(it) } ?: tooltip.defaultState
    }

    fun shouldShow(tooltip: TooltipKey): Boolean {
        when (getState(tooltip)) {
            TooltipState.SHOWN -> return false
            TooltipState.ALWAYS -> return true
            else -> Unit
        }

        if (getShowCount(tooltip) >= tooltip.maxTimeToShow) {
            return false
        }

        if (wasShownInSession(tooltip)) {
            return false
        }

        return true
    }

    fun getShowCount(tooltip: TooltipKey): Int {
        return prefs.getInt("${tooltip.tooltipName}_count", 0)
    }

    fun incrementShowCount(tooltip: TooltipKey) {
        prefs.edit {
            putInt("${tooltip.tooltipName}_count", getShowCount(tooltip) + 1)
        }
    }

    private fun resetShowCount(tooltip: TooltipKey) {
        prefs.edit {
            remove("${tooltip.tooltipName}_count")
        }
    }

    private val shownInSession = mutableSetOf<String>()

    fun wasShownInSession(tooltip: TooltipKey): Boolean {
        return tooltip.tooltipName in shownInSession
    }

    fun markShownInSession(tooltip: TooltipKey) {
        shownInSession.add(tooltip.tooltipName)
    }

    private fun resetSession(tooltip: TooltipKey) {
        shownInSession.remove(tooltip.tooltipName)
    }
}
