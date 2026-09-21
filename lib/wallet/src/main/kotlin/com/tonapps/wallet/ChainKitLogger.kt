package com.tonapps.wallet

import com.tonapps.chainkit.core.net.module.NetLogger
import com.tonapps.log.L

class ChainKitLogger : NetLogger {
    override fun log(message: String) {
        L.d(message)
    }
}
