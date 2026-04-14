package com.tonapps.deposit.screens.assets

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.deposit.data.AssetFilter
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.data.resolveAssets
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.mvi.MviFeature
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.mvi.MviSubject
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.runBlocking

sealed interface DepositCryptoExtendedAction : MviAction {
    data object Init : DepositCryptoExtendedAction
}

sealed interface DepositCryptoExtendedState : MviState {
    data class Data(
        val allAssets: List<WalletCurrency> = emptyList(),
        val otherAssets: List<WalletCurrency> = emptyList(),
    ) : DepositCryptoExtendedState
}

class DepositCryptoExtendedViewState(
    val global: MviProperty<DepositCryptoExtendedState>
) : MviViewState

data class AssetsCryptoExtendedFeatureData(
    val rampType: RampType,
    val filter: AssetFilter = AssetFilter.All,
)

private val tronUnsupportedWalletTypes = setOf(
    WalletType.Signer,
    WalletType.Lockup,
    WalletType.Ledger,
    WalletType.SignerQR,
    WalletType.Keystone,
)

class AssetsCryptoExtendedFeature(
    private val data: AssetsCryptoExtendedFeatureData,
    private val onRampRepository: ExchangeRepository,
    private val accountRepository: AccountRepository,
) : MviFeature<DepositCryptoExtendedAction, DepositCryptoExtendedState, DepositCryptoExtendedViewState>(
    initState = runBlocking {
        try {
            val walletType = accountRepository.getSelectedWallet()?.type
            val hideTron = walletType in tronUnsupportedWalletTypes
            onRampRepository.getLayout(data.rampType).resolveAssets(data.filter)
                .filterNot { hideTron && it.isTronChain }
        } catch (_: Throwable) {
            emptyList()
        }
    }.let { assets ->
        DepositCryptoExtendedState.Data(allAssets = assets, otherAssets = assets)
    },
    initAction = DepositCryptoExtendedAction.Init
) {

    private val searchQuery = MviSubject<String>()

    init {
        searchQuery.events
            .debounce(300)
            .mapLatest { query -> filterAssets(query) }
            .launchIn(bgScope)
    }

    fun setSearchQuery(query: String) {
        searchQuery.emit(query)
    }

    override fun createViewState(): DepositCryptoExtendedViewState {
        return buildViewState {
            DepositCryptoExtendedViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: DepositCryptoExtendedAction) {
        when (action) {
            is DepositCryptoExtendedAction.Init -> loadAssets()
        }
    }

    private suspend fun loadAssets() {
        try {
            val walletType = accountRepository.getSelectedWallet()?.type
            val hideTron = walletType in tronUnsupportedWalletTypes
            val layout = onRampRepository.getLayout(data.rampType)
            val assets = layout.resolveAssets(data.filter)
                .filterNot { hideTron && it.isTronChain }
            setState {
                DepositCryptoExtendedState.Data(
                    allAssets = assets,
                    otherAssets = assets,
                )
            }
        } catch (_: Throwable) {
            // keep initial state
        }
    }

    private fun filterAssets(query: String) {
        setState<DepositCryptoExtendedState.Data> {
            val trimmed = query.trim()
            val filtered = if (trimmed.isEmpty()) allAssets else allAssets.filter { it.containsQuery(trimmed) }
            copy(otherAssets = filtered)
        }
    }
}
