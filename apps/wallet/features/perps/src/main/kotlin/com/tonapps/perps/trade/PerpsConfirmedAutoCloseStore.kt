package com.tonapps.perps.trade

import android.content.Context
import androidx.core.content.edit
import com.tonapps.chainkit.perpetual.PerpsAutoCloseSpec
import com.tonapps.chainkit.perpetual.PerpsTradeIntent

class PerpsConfirmedAutoCloseStore(
    context: Context,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    fun record(walletId: String, spec: PerpsAutoCloseSpec?) = synchronized(lock) {
        val legs = listOfNotNull(spec?.takeProfit, spec?.stopLoss)
        if (legs.isEmpty()) {
            return@synchronized
        }
        val now = nowMs()
        val stale = staleConfirmedAutoCloseKeys(prefs.all, now)
        prefs.edit(commit = true) {
            stale.forEach { remove(it) }
            legs.forEach { putString("$walletId/${it.clientOrderIndex}", "${it.triggerPrice}/$now") }
        }
    }

    fun confirmed(walletId: String): Map<Long, Long> = confirmedAutoCloseTriggers(prefs.all, walletId)

    private companion object {
        const val PREFERENCES_NAME = "perps_confirmed_auto_close"
    }
}

internal fun confirmedAutoCloseTriggers(entries: Map<String, *>, walletId: String): Map<Long, Long> {
    val prefix = "$walletId/"

    return entries.mapNotNull { (key, value) ->
        val clientOrderIndex = key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toLongOrNull()
        val triggerPrice = recordedAutoCloseLeg(value)?.first
        if (clientOrderIndex != null && triggerPrice != null) {
            clientOrderIndex to triggerPrice
        } else {
            null
        }
    }.toMap()
}

internal fun staleConfirmedAutoCloseKeys(entries: Map<String, *>, nowMs: Long): List<String> {
    val keepAfterMs = nowMs - PerpsProductRules.AUTO_CLOSE_EXPIRY_MS - PerpsProductRules.AUTO_CLOSE_RENEWAL_LEAD_MS

    return entries.filter { (_, value) ->
        val recordedAtMs = recordedAutoCloseLeg(value)?.second
        recordedAtMs != null && recordedAtMs < keepAfterMs
    }.keys.toList()
}

internal fun PerpsTradeIntent.autoCloseSpec(): PerpsAutoCloseSpec? = when (this) {
    is PerpsTradeIntent.Open -> autoClose
    is PerpsTradeIntent.Add -> autoClose
    is PerpsTradeIntent.Close -> autoClose
    is PerpsTradeIntent.AutoClose -> autoClose
    else -> null
}

private fun recordedAutoCloseLeg(value: Any?): Pair<Long, Long>? {
    val parts = (value as? String)?.split('/')?.takeIf { it.size == 2 }
    val triggerPrice = parts?.get(0)?.toLongOrNull()
    val recordedAtMs = parts?.get(1)?.toLongOrNull()
    if (triggerPrice != null && recordedAtMs != null) {
        return triggerPrice to recordedAtMs
    }

    return null
}
