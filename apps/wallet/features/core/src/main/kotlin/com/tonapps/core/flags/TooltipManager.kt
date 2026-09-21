package com.tonapps.core.flags

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

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

    private val shownInSession = mutableSetOf<String>()

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
            resetDays(tooltip)
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

    fun shouldShowToday(tooltip: TooltipKey, placement: String): Boolean {
        when (getState(tooltip)) {
            TooltipState.SHOWN -> return false
            TooltipState.ALWAYS -> return true
            else -> Unit
        }

        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong(placementDayKey(tooltip, placement), -1L) == today) {
            return false
        }

        if (prefs.getLong(lastDayKey(tooltip), -1L) == today) {
            return true
        }

        return getShowCount(tooltip) < tooltip.maxTimeToShow
    }

    fun markShownToday(tooltip: TooltipKey, placement: String) {
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong(lastDayKey(tooltip), -1L) != today) {
            incrementShowCount(tooltip)
            prefs.edit {
                putLong(lastDayKey(tooltip), today)
            }
        }
        prefs.edit {
            putLong(placementDayKey(tooltip, placement), today)
        }
    }

    private fun lastDayKey(tooltip: TooltipKey): String {
        return "${tooltip.tooltipName}_last_day"
    }

    private fun placementDayKey(tooltip: TooltipKey, placement: String): String {
        return "${tooltip.tooltipName}_day_$placement"
    }

    private fun resetDays(tooltip: TooltipKey) {
        val placementPrefix = "${tooltip.tooltipName}_day_"
        val keys = prefs.all.keys.filter { it.startsWith(placementPrefix) } + lastDayKey(tooltip)
        prefs.edit {
            for (key in keys) {
                remove(key)
            }
        }
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
