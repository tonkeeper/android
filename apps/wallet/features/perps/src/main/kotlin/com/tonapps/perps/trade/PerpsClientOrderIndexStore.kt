package com.tonapps.perps.trade

import android.content.Context
import androidx.core.content.edit
import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException

class PerpsClientOrderIndexStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    private var last: Long? = null

    fun next(nowMs: Long): Long = synchronized(lock) {
        val floor = LighterConstants.MinClientOrderIndex - 1
        val persisted = prefs.getLong(KEY_LAST, floor)
        val previous = maxOf(last ?: floor, persisted)
        val candidate = maxOf(nowMs % LighterConstants.MaxClientOrderIndex, LighterConstants.MinClientOrderIndex)
        val next = maxOf(candidate, previous + 1)
        if (next >= LighterConstants.MaxClientOrderIndex) {
            throw PerpsTradeException(PerpsTradeError.ProtocolFailure, "client order index is exhausted")
        }
        prefs.edit(commit = true) { putLong(KEY_LAST, next) }
        last = next

        next
    }

    private companion object {
        const val PREFERENCES_NAME = "perps_client_order_index"
        const val KEY_LAST = "last"
    }
}
