package com.tonapps.wallet.api

import android.content.Context
import android.os.SystemClock
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.prefs
import androidx.core.content.edit

internal class ServerTimeProvider(context: Context) {

    private companion object {
        const val SERVER_TIME_KEY = "server_time"
        const val LOCAL_TIME_KEY = "local_time"

        // 24 hours in milliseconds
        private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L

        private fun getServerTimePrefKey(network: TonNetwork) = "${SERVER_TIME_KEY}_${network.name.lowercase()}"

        private fun getLocalTimePrefKey(network: TonNetwork) = "${LOCAL_TIME_KEY}_${network.name.lowercase()}"

    }

    private val prefs = context.prefs("server_time")

    fun setServerTime(network: TonNetwork, serverTimeSeconds: Int) {
        val localTimeMillis = SystemClock.elapsedRealtime()

        prefs.edit {
            putInt(getServerTimePrefKey(network), serverTimeSeconds)
            putLong(getLocalTimePrefKey(network), localTimeMillis)
        }
    }

    fun getServerTime(network: TonNetwork): Int? {
        val savedServerSeconds = prefs.getInt(getServerTimePrefKey(network), 0)
        val savedLocalMillis = prefs.getLong(getLocalTimePrefKey(network), 0L)
        if (0 >= savedServerSeconds || 0 >= savedLocalMillis) {
            return null
        }
        val elapsedTimeMillis = SystemClock.elapsedRealtime() - savedLocalMillis
        if (elapsedTimeMillis > CACHE_EXPIRATION_MS) {
            return null
        }
        val elapsedSeconds = elapsedTimeMillis / 1000
        val currentServerTime = savedServerSeconds + elapsedSeconds
        return currentServerTime.toInt()
    }
}