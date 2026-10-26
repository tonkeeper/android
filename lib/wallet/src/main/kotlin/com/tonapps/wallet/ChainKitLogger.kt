package com.tonapps.wallet

import com.tonapps.chainkit.core.net.module.LoggerLevel
import com.tonapps.chainkit.core.net.module.NetLogger
import com.tonapps.log.L
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ChainKitLogger : NetLogger {
    override fun log(message: String, details: Map<String, String>, level: LoggerLevel) {
        val line = if (details.isEmpty()) {
            message
        } else {
            "$message ${JsonObject(details.mapValues { JsonPrimitive(it.value) })}"
        }
        when (level) {
            LoggerLevel.Debug -> L.d(line)
            LoggerLevel.Info -> L.i(line)
            LoggerLevel.Warning -> L.w(line)
            LoggerLevel.Error -> L.e(line)
        }
    }
}
