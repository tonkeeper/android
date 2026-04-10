package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.api.API
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Locale

fun SettingsRepository.isSafeModeEnabled(api: API, network: TonNetwork): Boolean {
    val state = getSafeModeState()
    if (state == SafeModeState.Default) {
        return api.getConfig(network).flags.safeModeEnabled
    }
    return state == SafeModeState.Enabled
}