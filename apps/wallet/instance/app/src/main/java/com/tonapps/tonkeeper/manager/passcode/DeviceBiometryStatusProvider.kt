package com.tonapps.tonkeeper.manager.passcode

import android.content.Context
import com.tonapps.portfolio.screens.wallet.BiometryStatusProvider
import com.tonapps.wallet.data.passcode.PasscodeBiometric

class DeviceBiometryStatusProvider(
    private val context: Context,
) : BiometryStatusProvider {

    override fun isBiometryAvailable(): Boolean {
        return PasscodeBiometric.isAvailableOnDevice(context)
    }
}
