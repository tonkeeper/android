package com.tonapps.deposit.screens.network

import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.data.pairedCryptoNetworkInfos
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty

sealed interface SelectNetworkAction : MviAction {
    data object Init : SelectNetworkAction
}

sealed interface SelectNetworkState : MviState {
    data object Loading : SelectNetworkState
    data object Empty : SelectNetworkState
    data class Data(val networks: List<CryptoNetworkInfo>) : SelectNetworkState
}

class SelectNetworkViewState(
    val global: MviProperty<SelectNetworkState>
) : MviViewState

data class SelectNetworkData(
    val stablecoinCode: String,
    val stablecoinNetwork: String?,
    val rampType: RampType,
    val selectedSymbol: String? = null,
)

class SelectNetworkFeature(
    val data: SelectNetworkData,
    private val exchangeRepository: ExchangeRepository,
) : MviFeature<SelectNetworkAction, SelectNetworkState, SelectNetworkViewState>(
    initState = SelectNetworkState.Loading,
    initAction = SelectNetworkAction.Init,
) {

    override fun createViewState(): SelectNetworkViewState {
        return buildViewState {
            SelectNetworkViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: SelectNetworkAction) {
        when (action) {
            SelectNetworkAction.Init -> {
                val layout = exchangeRepository.getLayout(data.rampType)
                val allNetworks = layout.pairedCryptoNetworkInfos(data.stablecoinCode, data.stablecoinNetwork)
                val networks = if (data.selectedSymbol != null) {
                    allNetworks.filter { it.currency.code.equals(data.selectedSymbol, ignoreCase = true) }
                } else {
                    allNetworks
                }
                if (networks.isEmpty()) {
                    setState { SelectNetworkState.Empty }
                } else {
                    setState { SelectNetworkState.Data(networks) }
                }
            }
        }
    }
}
