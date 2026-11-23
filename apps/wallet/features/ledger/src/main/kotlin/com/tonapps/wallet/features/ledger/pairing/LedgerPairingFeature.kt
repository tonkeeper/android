package com.tonapps.wallet.features.ledger.pairing

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleEnvironment
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.ledgerkit.transport.ble.LedgerBleScanner
import com.tonapps.ledgerkit.transport.usb.LedgerUsbDevice
import com.tonapps.ledgerkit.transport.usb.LedgerUsbEnvironment
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.LedgerLegacyTwins
import com.tonapps.wallet.data.ledger.LedgerLegacyWallet
import com.tonapps.wallet.data.ledger.LedgerWalletInteractor
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveredAccount
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractor
import com.tonapps.wallet.data.ledger.discovery.LedgerImportCandidate
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.bus.walletimport.WalletImportPairingSessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class LedgerPairingFeature(
    private val mode: LedgerPairingMode,
    private val environment: LedgerBleEnvironment,
    private val scanner: LedgerBleScanner,
    private val usb: LedgerUsbEnvironment,
    private val controller: LedgerController,
    private val discovery: LedgerDiscoveryInteractor,
    private val walletInteractor: LedgerWalletInteractor,
    private val accountRepository: McAccountRepository,
    pairingSessions: WalletImportPairingSessions,
) : AsyncViewModel(), LedgerPairingActions {

    private val relay = MviRelay<LedgerPairingEvent>()
    val events = relay.events

    private val analytics = LedgerPairingAnalytics(
        mode = mode,
        pairingSession = (mode as? LedgerPairingMode.New)?.let { pairingSessions.active(WalletFlowWalletSource.Ledger) },
    )

    val state: StateFlow<LedgerPairingState>
        field = MutableStateFlow(
            LedgerPairingState(
                isRepair = mode is LedgerPairingMode.Repair,
                isMigration = mode is LedgerPairingMode.Migrate,
                readiness = environment.check(),
            ),
        )

    val operation: StateFlow<LedgerOperationState> = controller.state

    private val discovered = linkedMapOf<Int, LedgerDiscoveredAccount>()
    private var pagingApps: List<LedgerChainApp> = listOf(LedgerChainApp.Ton)
    private var scanJob: Job? = null
    private var usbJob: Job? = null
    private var workJob: Job? = null
    private var connectionJob: Job? = null

    private val repairWalletId: String?
        get() = (mode as? LedgerPairingMode.Repair)?.walletId

    init {
        analytics.started()
        (mode as? LedgerPairingMode.Migrate)?.let { loadMigrationTarget(it.walletId) }
    }

    override fun onPermissionsResult(granted: Boolean) {
        analytics.permissionsResult(granted)
        state.update { it.copy(readiness = readiness(), isPermissionDenied = !granted) }
        if (granted && state.value.step == LedgerPairingStep.Connect) {
            startScan()
        }
    }

    override fun retryScan() {
        state.update { it.copy(readiness = readiness()) }
        startScan()
    }

    override fun selectDevice(bleDevice: LedgerBleDevice) {
        select(LedgerDevice(bleDevice.address, bleDevice.name, bleDevice.model))
    }

    override fun selectUsbDevice(usbDevice: LedgerUsbDevice) {
        select(LedgerDevice(usbDevice.deviceId, usbDevice.displayName, usbDevice.model))
    }

    private fun select(device: LedgerDevice) {
        if (workJob?.isActive == true) {
            return
        }
        val migration = state.value.migration
        if (mode is LedgerPairingMode.Migrate && migration == null) {
            return
        }
        stopScan()
        val repairId = repairWalletId
        val apps = if (repairId != null) {
            listOf(LedgerChainApp.Ton)
        } else {
            LedgerChainApp.entries.toList()
        }
        state.update { it.copy(device = device, step = LedgerPairingStep.Discovery, apps = apps.map(::pendingRow), scanFailure = null) }
        watchConnection(device)
        workJob = bgScope.launch {
            when {
                repairId != null -> rebind(repairId, device)
                migration != null -> migrate(device, migration, apps)
                else -> discover(device, (0 until PAGE_SIZE).toList(), apps)
            }
        }
    }

    override fun retryOperation() {
        controller.retry()
    }

    override fun loadMore() {
        val device = state.value.device ?: return
        if (workJob?.isActive == true) {
            return
        }
        val start = (discovered.keys.maxOrNull() ?: -1) + 1
        val indices = (start until start + PAGE_SIZE).filter { it <= LedgerDerivation.MAX_ACCOUNT_INDEX }
        if (indices.isEmpty()) {
            return
        }
        val apps = pagingApps
        state.update { it.copy(step = LedgerPairingStep.Discovery, apps = apps.map(::pendingRow)) }
        workJob = bgScope.launch {
            discover(device, indices, apps)
        }
    }

    override fun toggle(index: Int) {
        state.update { current ->
            val row = current.accounts.firstOrNull { it.index == index }
            if (row == null || row.isAlreadyAdded) {
                current
            } else if (index in current.selected) {
                current.copy(selected = current.selected - index)
            } else {
                current.copy(selected = current.selected + index)
            }
        }
    }

    override fun importSelected() {
        val current = state.value
        val device = current.device ?: return
        if (current.isImporting || workJob?.isActive == true) {
            return
        }
        val candidates = current.selected.sorted().mapNotNull { discovered[it] }
            .map { LedgerImportCandidate.from(device, it) }
        if (candidates.isEmpty()) {
            return
        }
        state.update { it.copy(isImporting = true) }
        workJob = bgScope.launch {
            importCandidates(device, candidates)
        }
    }

    private suspend fun importCandidates(device: LedgerDevice, candidates: List<LedgerImportCandidate>) {
        try {
            walletInteractor.prepareWalletSeeds(device, candidates)
            analytics.handedOff()
            relay.emit(LedgerPairingEvent.Import(LedgerImportCandidate.encode(candidates), device.name))
        } catch (e: CancellationException) {
            state.update { it.copy(isImporting = false) }
            throw e
        } catch (e: LedgerFailureException) {
            L.e(e)
            analytics.failed(e.failure, device)
            state.update { it.copy(isImporting = false) }
            if (!e.isPresented) {
                relay.emit(LedgerPairingEvent.Failed(e.failure))
            }
            if (mode is LedgerPairingMode.Migrate) {
                state.update { it.copy(device = null) }
                openConnect()
            }
        }
    }

    private fun loadMigrationTarget(walletId: String) {
        bgScope.launch {
            val wallet = try {
                walletInteractor.legacyWallet(walletId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e(e)
                LedgerNonFatals.recordIssue(e, LedgerIssueSite.Migration)
                null
            }
            if (wallet == null) {
                relay.emit(LedgerPairingEvent.Close)
                return@launch
            }
            state.update { it.copy(migration = wallet) }
        }
    }

    private suspend fun migrate(device: LedgerDevice, target: LedgerLegacyWallet, apps: List<LedgerChainApp>) {
        try {
            val result = discovery.discover(
                device = device,
                indices = listOf(target.accountIndex),
                apps = apps,
                expectedTonAddress = target.tonAddress,
            ) { app, appState ->
                state.update { it.copy(apps = it.apps.withState(app, appState)) }
            }
            analytics.appsSkipped(device, result.skipped)
            val account = result.accounts.firstOrNull { it.index == target.accountIndex }
            val tonKey = account?.accounts?.firstOrNull { it.chain == Chain.Ton.Mainnet }?.publicKeyHex
            if (account == null || tonKey == null || !LedgerLegacyTwins.isSameKey(tonKey, target.tonPublicKeyHex)) {
                analytics.failed(LedgerFailure.AddressMismatch(LedgerAppId.Ton), device)
                state.update {
                    it.copy(
                        step = LedgerPairingStep.Connect,
                        device = null,
                        devices = emptyList(),
                        scanFailure = LedgerFailure.AddressMismatch(LedgerAppId.Ton),
                    )
                }
                return
            }
            discovered[account.index] = account
            state.update { it.copy(selected = setOf(account.index), isImporting = true) }
            importCandidates(device, listOf(LedgerImportCandidate.from(device, account)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerFailureException) {
            L.e(e)
            returnAfterFailure(e.failure, device)
        } catch (e: Exception) {
            L.e(e)
            LedgerNonFatals.recordIssue(e, LedgerIssueSite.Migration)
            returnAfterFailure(LedgerFailure.Unexpected(e.message ?: e.javaClass.simpleName), device)
        }
    }

    override fun back() {
        when (state.value.step) {
            LedgerPairingStep.Connect -> relay.emit(LedgerPairingEvent.Close)
            LedgerPairingStep.Discovery -> cancelWork()
            LedgerPairingStep.Accounts -> if (state.value.isImporting) {
                controller.cancel()
            } else {
                discovered.clear()
                state.update { it.copy(accounts = emptyList(), selected = emptySet(), device = null) }
                openConnect()
            }
        }
    }

    fun onScreenHidden() {
        stopScan()
        usbJob?.cancel()
        usbJob = null
    }

    fun refreshReadiness() {
        val readiness = readiness()
        state.update { it.copy(readiness = readiness) }
        if (state.value.step != LedgerPairingStep.Connect) {
            return
        }
        if (readiness != LedgerBleReadiness.Ready) {
            stopScan()
            state.update { it.copy(devices = emptyList(), scanFailure = null) }
        } else if (scanJob?.isActive != true && state.value.scanFailure == null) {
            startScan()
        }
    }

    fun onScreenVisible() {
        refreshReadiness()
        watchUsb()
    }

    override fun onCleared() {
        stopScan()
        usbJob?.cancel()
        analytics.cleared()
        super.onCleared()
    }

    private fun readiness(): LedgerBleReadiness {
        val readiness = environment.check()
        analytics.readiness(readiness)
        return readiness
    }

    private fun watchUsb() {
        usbJob?.cancel()
        usbJob = bgScope.launch {
            usb.observe().collect { devices ->
                if (state.value.step == LedgerPairingStep.Connect) {
                    analytics.usbDevicesFound(devices)
                }
                state.update { it.copy(usbDevices = devices) }
            }
        }
    }

    private fun cancelWork() {
        if (controller.state.value !is LedgerOperationState.Idle) {
            controller.cancel()
            return
        }
        workJob?.cancel()
        state.update { it.copy(device = null) }
        openConnect()
    }

    private fun openConnect() {
        state.update { it.copy(step = LedgerPairingStep.Connect, scanFailure = null) }
        startScan()
    }

    private fun startScan() {
        scanJob?.cancel()
        val readiness = readiness()
        state.update { it.copy(devices = emptyList(), scanFailure = null, readiness = readiness) }
        if (readiness != LedgerBleReadiness.Ready) {
            return
        }
        controller.closeIdleSession()
        analytics.searchStarted()
        scanJob = bgScope.launch {
            try {
                scanner.scan().collect { devices ->
                    analytics.bleDevicesFound(devices)
                    state.update { it.copy(devices = devices) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LedgerFailureException) {
                LedgerNonFatals.recordIssue(e, LedgerIssueSite.Pairing)
                state.update { it.copy(scanFailure = e.failure, readiness = readiness()) }
            }
        }
    }

    private fun watchConnection(device: LedgerDevice) {
        connectionJob?.cancel()
        if (mode !is LedgerPairingMode.New) {
            return
        }
        connectionJob = bgScope.launch {
            val link = controller.link.filterNotNull().first { it.deviceId.equals(device.id, ignoreCase = true) }
            analytics.connected(device, link.kind)
            controller.link.filterNotNull().first { it.deviceId.equals(device.id, ignoreCase = true) && it.app?.appId == LedgerAppId.Ton }
            analytics.tonAppOpened(device)
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private suspend fun discover(device: LedgerDevice, indices: List<Int>, apps: List<LedgerChainApp>) {
        try {
            val result = discovery.discover(device = device, indices = indices, apps = apps) { app, appState ->
                state.update { it.copy(apps = it.apps.withState(app, appState)) }
            }
            analytics.appsSkipped(device, result.skipped)
            result.accounts.forEach { discovered[it.index] = it }
            pagingApps = result.apps.filterValues { it == LedgerAppDiscoveryState.Done }.keys.toList().ifEmpty { listOf(LedgerChainApp.Ton) }
            analytics.accountsListed(device, pagingApps)
            val rows = discovered.values.map { account -> account.toRow() }
            val maxIndex = discovered.keys.maxOrNull() ?: 0
            state.update { current ->
                val selected = if (current.accounts.isEmpty()) {
                    rows.firstOrNull { !it.isAlreadyAdded }?.let { setOf(it.index) }.orEmpty()
                } else {
                    current.selected
                }
                current.copy(
                    step = LedgerPairingStep.Accounts,
                    accounts = rows,
                    selected = selected,
                    apps = result.apps.map { (app, appState) -> LedgerAppRow(app, appState) },
                    canLoadMore = maxIndex < LedgerDerivation.MAX_ACCOUNT_INDEX,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerFailureException) {
            L.e(e)
            returnAfterFailure(e.failure, device)
        } catch (e: Exception) {
            L.e(e)
            LedgerNonFatals.recordIssue(e, LedgerIssueSite.Pairing)
            returnAfterFailure(LedgerFailure.Unexpected(e.message ?: e.javaClass.simpleName), device)
        }
    }

    private suspend fun rebind(walletId: String, device: LedgerDevice) {
        try {
            walletInteractor.rebind(walletId, device)
            relay.emit(LedgerPairingEvent.Repaired)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerFailureException) {
            L.e(e)
            returnAfterFailure(e.failure, device)
        } catch (e: Exception) {
            L.e(e)
            LedgerNonFatals.recordIssue(e, LedgerIssueSite.Pairing)
            returnAfterFailure(LedgerFailure.Unexpected(e.message ?: e.javaClass.simpleName), device)
        }
    }

    private fun returnAfterFailure(failure: LedgerFailure, device: LedgerDevice) {
        analytics.failed(failure, device)
        if (state.value.accounts.isNotEmpty()) {
            state.update { it.copy(step = LedgerPairingStep.Accounts, apps = pagingApps.map { app -> LedgerAppRow(app, LedgerAppDiscoveryState.Done) }) }
            return
        }
        state.update { it.copy(device = null) }
        if (failure == LedgerFailure.Busy) {
            state.update { it.copy(step = LedgerPairingStep.Connect, devices = emptyList(), scanFailure = failure) }
        } else {
            openConnect()
        }
    }

    private suspend fun LedgerDiscoveredAccount.toRow(): LedgerAccountRow {
        val ton = accounts.first { it.chain == Chain.Ton.Mainnet }
        return LedgerAccountRow(
            index = index,
            addresses = LedgerChainApp.entries.mapNotNull { app ->
                accounts.firstOrNull { it.chain in app.chains }?.let { LedgerAppAddress(app, it.displayAddress) }
            },
            isAlreadyAdded = isAlreadyAdded(ton.displayAddress),
        )
    }

    private suspend fun isAlreadyAdded(tonAddress: String): Boolean = try {
        accountRepository.findWalletIdsByAddress(Chain.Ton.Mainnet, tonAddress).isNotEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        L.e(e)
        LedgerNonFatals.recordIssue(e, LedgerIssueSite.Pairing)
        false
    }

    private fun pendingRow(app: LedgerChainApp) = LedgerAppRow(app, LedgerAppDiscoveryState.Pending)

    private fun List<LedgerAppRow>.withState(app: LedgerChainApp, appState: LedgerAppDiscoveryState) =
        map { row -> if (row.app == app) { row.copy(state = appState) } else { row } }

    private companion object {
        const val PAGE_SIZE = 3
    }
}
