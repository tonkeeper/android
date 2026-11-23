package com.tonapps.perps.trade

import android.content.Context
import androidx.core.content.edit
import com.tonapps.perps.data.MIN_LEVERAGE

class PerpsOpenLeverageStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun leverage(walletId: String, marketIndex: Int): Int? {
        return prefs.getInt(key(walletId, marketIndex), 0).takeIf { it >= MIN_LEVERAGE }
    }

    fun remember(walletId: String, marketIndex: Int, leverage: Int) {
        prefs.edit { putInt(key(walletId, marketIndex), leverage) }
    }

    private fun key(walletId: String, marketIndex: Int): String = "$walletId.$marketIndex"

    private companion object {
        const val PREFERENCES_NAME = "perps_open_leverage"
    }
}
