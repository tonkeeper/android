package com.tonapps.wallet.features.ledger.wallet

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDeviceViewFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDiscoveryErrorFrom
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.LedgerWalletInteractor
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.tonapps.wallet.data.ledger.analytics.ledgerAppsSkipped
import com.tonapps.wallet.data.ledger.analytics.ledgerFailure
import com.tonapps.wallet.data.ledger.analytics.ledgerModel
import com.tonapps.wallet.data.ledger.analytics.toAnalyticsApps
import com.tonapps.wallet.data.ledger.analytics.toFailureType
import com.tonapps.wallet.data.ledger.analytics.toLedgerFlowModel
import com.tonapps.wallet.data.ledger.connection.LedgerActivity
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionInteractor
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionStatus
import com.tonapps.wallet.data.ledger.connection.LedgerScanPolicy
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class LedgerWalletFeature(
    private val walletId: String,
    from: LedgerDeviceViewFrom,
    private val accountRepository: McAccountRepository,
    private val walletInteractor: LedgerWalletInteractor,
    private val signingInteractor: LedgerSigningInteractor,
    private val connectionInteractor: LedgerConnectionInteractor,
) : AsyncViewModel(), LedgerWalletActions {

    private val relay = MviRelay<LedgerWalletEvent>()
    val events = relay.events

    val state: StateFlow<LedgerWalletState>
        field = MutableStateFlow(LedgerWalletState())

    val connection: StateFlow<LedgerConnectionStatus?> = connectionInteractor.status(walletId, LedgerScanPolicy.Detailed)
        .stateIn(bgScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activity: StateFlow<List<LedgerActivity>> = connectionInteractor.activity(walletId)
        .stateIn(bgScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var workJob: Job? = null

    private val analytics
        get() = AnalyticsHelper.Default.events.ledgerFlow

    init {
        analytics.ledgerDeviceView(from)
        bgScope.launch {
            accountRepository.refreshTrigger.collect { reload() }
        }
        bgScope.launch { reload() }
    }

    override fun verify(chain: Chain) {
        if (workJob?.isActive == true) {
            return
        }
        state.update { it.copy(busyChain = chain) }
        workJob = bgScope.launch {
            try {
                signingInteractor.verifyAddress(walletId, chain)
                relay.emit(LedgerWalletEvent.AddressVerified)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(e)
            } finally {
                state.update { it.copy(busyChain = null) }
            }
        }
    }

    override fun addNetworks() {
        if (workJob?.isActive == true) {
            return
        }
        state.update { it.copy(isAddingNetworks = true) }
        analytics.ledgerAddNetworksStart()
        workJob = bgScope.launch {
            try {
                val result = walletInteractor.addMissingNetworks(walletId)
                val ledgerModel = accountRepository.ledgerModel(walletId)
                analytics.ledgerAddNetworksSuccess(
                    addedApps = result.added.map(LedgerChainApp::forChain).distinct().toAnalyticsApps(),
                    ledgerModel = ledgerModel?.toLedgerFlowModel(),
                )
                analytics.ledgerAppsSkipped(LedgerDiscoveryErrorFrom.AddNetworks, result.skipped, ledgerModel)
                relay.emit(LedgerWalletEvent.NetworksAdded(result.added, skipReason = result.skipped.values.firstOrNull()))
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trackAddNetworksFailure(e)
                reportFailure(e)
            } finally {
                state.update { it.copy(isAddingNetworks = false) }
            }
        }
    }

    override fun checkConnection() {
        if (workJob?.isActive == true) {
            return
        }
        workJob = bgScope.launch {
            try {
                val app = connectionInteractor.checkConnection(walletId)
                relay.emit(LedgerWalletEvent.ConnectionChecked(app.takeUnless { it.isDashboard }?.appId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LedgerFailureException) {
                if (e.failure != LedgerFailure.Cancelled) {
                    relay.emit(LedgerWalletEvent.Failed(e.failure))
                }
            } catch (e: Exception) {
                reportFailure(e)
            }
        }
    }

    override fun cancelCheck() {
        connectionInteractor.cancelCheck()
    }

    override fun retryLoad() {
        state.update { it.copy(isLoaded = false, isLoadFailed = false) }
        bgScope.launch { reload() }
    }

    private suspend fun trackAddNetworksFailure(error: Exception) {
        val failure = error.ledgerFailure
        if (failure == LedgerFailure.Cancelled) {
            return
        }
        analytics.ledgerAddNetworksError(
            failureType = failure?.toFailureType(),
            ledgerModel = accountRepository.ledgerModel(walletId)?.toLedgerFlowModel(),
        )
    }

    private fun reportFailure(error: Exception) {
        L.e(error)
        LedgerNonFatals.recordIssue(error, LedgerIssueSite.DeviceScreen)
        val ledgerFailure = error as? LedgerFailureException
        if (ledgerFailure?.isPresented == true) {
            return
        }
        relay.emit(LedgerWalletEvent.Failed(ledgerFailure?.failure))
    }

    private suspend fun reload() {
        try {
            val descriptor = accountRepository.getHardwareDescriptor(walletId)
            if (descriptor == null) {
                relay.emit(LedgerWalletEvent.Close)
                return
            }
            val accounts = accountRepository.getCoinAccounts(walletId)
            val networks = accounts
                .distinctBy { it.chain }
                .sortedBy { account -> LedgerChainApp.entries.indexOfFirst { account.chain in it.chains } }
                .map { LedgerNetworkRow(it.id, it.chain, it.displayAddress) }
            val missingNetworks = walletInteractor.missingChains(walletId)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(NETWORK_SEPARATOR) { it.name }
            val device = LedgerDeviceInfo(
                deviceName = descriptor.deviceName,
                modelName = LedgerModel.fromName(descriptor.deviceModel)?.productName?.takeIf { it != descriptor.deviceName },
                accountNumber = descriptor.accountIndex + 1,
            )
            state.update {
                it.copy(device = device, networks = networks, missingNetworks = missingNetworks, isLoaded = true, isLoadFailed = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            LedgerNonFatals.recordIssue(e, LedgerIssueSite.DeviceScreen)
            state.update { it.copy(isLoaded = true, isLoadFailed = it.device == null) }
        }
    }

    private companion object {
        const val NETWORK_SEPARATOR = " · "
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
