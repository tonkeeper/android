package com.tonapps.tonkeeper

import android.os.Build
import com.tonapps.bus.core.IssueHelper
import com.tonapps.wallet.ChainKitProvider

object NativeLibraries {

    var isUnavailable: Boolean = false
        private set

    fun load(): Boolean {
        try {
            ChainKitProvider.init()
            return true
        } catch (e: UnsatisfiedLinkError) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                throw e
            }
            isUnavailable = true
            IssueHelper.recordException(e)
            return false
        }
    }
}
