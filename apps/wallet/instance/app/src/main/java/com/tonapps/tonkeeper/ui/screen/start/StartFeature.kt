package com.tonapps.tonkeeper.ui.screen.start

import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import kotlinx.coroutines.launch

class StartFeature(
    private val deviceRepository: SecureDeviceRepository,
) : AsyncViewModel() {

    fun registerDevice() {
        bgScope.launch {
            deviceRepository.registerIfNeeded()
        }
    }
}
