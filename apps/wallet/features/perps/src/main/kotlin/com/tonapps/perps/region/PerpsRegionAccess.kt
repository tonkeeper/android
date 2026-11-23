package com.tonapps.perps.region

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.screens.details.PerpsAssetDetailsTarget
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.PerpsAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PerpsRegionAccess(private val api: API) {

    val isRestricted: Boolean
        get() = api.getConfig(TonNetwork.MAINNET).perpsAccess == PerpsAccess.Restricted

    val restricted: Flow<Boolean> = api.configFlow
        .map { it.perpsAccess == PerpsAccess.Restricted }
        .distinctUntilChanged()

    suspend fun refresh() {
        if (!api.hasCountryConfig) {
            return
        }
        try {
            api.refreshConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
        }
    }
}

internal val PerpsAssetDetailsTarget.isBlockedInRestrictedRegion: Boolean
    get() = when (this) {
        is PerpsAssetDetailsTarget.Order -> true
        is PerpsAssetDetailsTarget.CashOut -> false
        is PerpsAssetDetailsTarget.SizeChange -> direction == PerpsSizeDirection.ADD
        is PerpsAssetDetailsTarget.MarginChange -> add
    }
