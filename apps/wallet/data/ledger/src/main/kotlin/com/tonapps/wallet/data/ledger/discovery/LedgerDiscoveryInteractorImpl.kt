package com.tonapps.wallet.data.ledger.discovery

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.app.btc.BtcApp
import com.tonapps.ledgerkit.app.btc.BtcScripts
import com.tonapps.ledgerkit.app.eth.EthApp
import com.tonapps.ledgerkit.app.solana.SolanaApp
import com.tonapps.ledgerkit.app.tron.TronApp
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.wallet.data.ledger.LedgerAddresses
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.reportingLedgerIssues
import com.tonapps.wallet.data.ledger.signing.TonKeyReader
import com.tonapps.wallet.data.ledger.signing.toHexString
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft

internal class LedgerDiscoveryInteractorImpl(private val controller: LedgerController) : LedgerDiscoveryInteractor {

    override suspend fun discover(
        device: LedgerDevice,
        indices: List<Int>,
        apps: List<LedgerChainApp>,
        purpose: LedgerPurpose,
        walletId: String?,
        expectedTonAddress: String?,
        onAppState: (LedgerChainApp, LedgerAppDiscoveryState) -> Unit,
    ): LedgerDiscoveryResult = reportingLedgerIssues(LedgerIssueSite.Discovery) {
        require(indices.all { it in 0..LedgerDerivation.MAX_ACCOUNT_INDEX }) { "Unsupported Ledger account index" }
        val collected = linkedMapOf<LedgerChainApp, AppKeys>()
        val states = linkedMapOf<LedgerChainApp, LedgerAppDiscoveryState>()
        apps.forEach { states[it] = LedgerAppDiscoveryState.Pending }
        controller.run(LedgerRequest(device.id, device.name, purpose, walletId)) {
            var verifiedLockEvents: Int? = null
            for (app in apps) {
                if (collected.containsKey(app) || states[app] is LedgerAppDiscoveryState.Skipped) {
                    continue
                }
                update(states, app, LedgerAppDiscoveryState.InProgress, onAppState)
                try {
                    collected[app] = readOnTonSeed(this, app, indices, collected[LedgerChainApp.Ton], expectedTonAddress, verifiedLockEvents)
                    verifiedLockEvents = session.lockEvents
                    update(states, app, LedgerAppDiscoveryState.Done, onAppState)
                } catch (e: LedgerFailureException) {
                    if (app.isRequired || !e.failure.isSkippable()) {
                        update(states, app, LedgerAppDiscoveryState.Pending, onAppState)
                        throw e
                    }
                    update(states, app, LedgerAppDiscoveryState.Skipped(e.failure), onAppState)
                }
            }
        }
        val accounts = indices.map { index ->
            val bitcoin = collected[LedgerChainApp.Bitcoin]?.bitcoin?.get(index)
            LedgerDiscoveredAccount(
                index = index,
                accounts = collected.values.flatMap { it.drafts[index].orEmpty() },
                bitcoinMasterFingerprint = bitcoin?.fingerprint,
                bitcoinAccountXpub = bitcoin?.accountXpub,
            )
        }
        LedgerDiscoveryResult(accounts, states.toMap())
    }

    private suspend fun readOnTonSeed(
        scope: LedgerScope,
        app: LedgerChainApp,
        indices: List<Int>,
        tonKeys: AppKeys?,
        expectedTonAddress: String?,
        verifiedLockEvents: Int?,
    ): AppKeys {
        repeat(MAX_SEED_CHECKS) {
            val keys = readApp(scope, app, indices)
            if (app == LedgerChainApp.Ton) {
                if (expectedTonAddress != null) {
                    requireTonAddress(keys, expectedTonAddress)
                }
                return keys
            }
            val readLockEvents = scope.session.lockEvents
            if (tonKeys == null || readLockEvents == verifiedLockEvents) {
                return keys
            }
            requireSameTonSeed(scope, tonKeys, indices.first())
            if (scope.session.lockEvents == readLockEvents) {
                return keys
            }
        }
        throw LedgerFailureException(LedgerFailure.DeviceLocked)
    }

    private suspend fun requireSameTonSeed(scope: LedgerScope, keys: AppKeys, index: Int) {
        val stored = keys.drafts[index]?.firstOrNull { it.chain == Chain.Ton.Mainnet }?.publicKeyHex
        val current = LedgerAddresses.hexWithPrefix(TonKeyReader.readPublicKey(scope, index, display = false, expectedAddress = null))
        if (stored == null || !stored.equals(current, ignoreCase = true)) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Ton))
        }
    }

    private fun requireTonAddress(keys: AppKeys, expectedTonAddress: String) {
        val matches = keys.drafts.values.flatten().any { it.chain == Chain.Ton.Mainnet && it.displayAddress == expectedTonAddress }
        if (!matches) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Ton))
        }
    }

    private fun update(
        states: MutableMap<LedgerChainApp, LedgerAppDiscoveryState>,
        app: LedgerChainApp,
        state: LedgerAppDiscoveryState,
        onAppState: (LedgerChainApp, LedgerAppDiscoveryState) -> Unit,
    ) {
        states[app] = state
        onAppState(app, state)
    }

    private suspend fun readApp(scope: LedgerScope, app: LedgerChainApp, indices: List<Int>): AppKeys = when (app) {
        LedgerChainApp.Ton -> readTon(scope, indices)
        LedgerChainApp.Ethereum -> readEthereum(scope, indices)
        LedgerChainApp.Tron -> readTron(scope, indices)
        LedgerChainApp.Bitcoin -> readBitcoin(scope, indices)
        LedgerChainApp.Solana -> readSolana(scope, indices)
    }

    private suspend fun readTon(scope: LedgerScope, indices: List<Int>): AppKeys {
        scope.ensureApp(LedgerAppId.Ton)
        val drafts = indices.associateWith { index ->
            val publicKey = TonKeyReader.readPublicKey(scope, index, display = false, expectedAddress = null)
            listOf(draft(Chain.Ton.Mainnet, Address.Type.TonV4R2, publicKey))
        }
        return AppKeys(drafts)
    }

    private suspend fun readEthereum(scope: LedgerScope, indices: List<Int>): AppKeys {
        scope.ensureApp(LedgerAppId.Ethereum)
        val app = EthApp(scope.session)
        val drafts = indices.associateWith { index ->
            val account = app.getAddress(LedgerDerivation.ethereum(index), display = false)
            LedgerChainApp.Ethereum.chains.map { chain ->
                val drafted = draft(chain, Address.Type.Default, account.publicKey)
                if (!drafted.displayAddress.equals(account.address, ignoreCase = true)) {
                    throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Ethereum))
                }
                drafted
            }
        }
        return AppKeys(drafts)
    }

    private suspend fun readTron(scope: LedgerScope, indices: List<Int>): AppKeys {
        scope.ensureApp(LedgerAppId.Tron)
        val app = TronApp(scope.session)
        val drafts = indices.associateWith { index ->
            val account = app.getAddress(LedgerDerivation.tron(index), display = false)
            val drafted = draft(Chain.Tron.Mainnet, Address.Type.Default, account.publicKey)
            if (drafted.displayAddress != account.address) {
                throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Tron))
            }
            listOf(drafted)
        }
        return AppKeys(drafts)
    }

    private suspend fun readSolana(scope: LedgerScope, indices: List<Int>): AppKeys {
        val info = scope.ensureApp(LedgerAppId.Solana)
        if (!info.isAtLeast(SolanaApp.MIN_APP_VERSION)) {
            throw LedgerFailureException(LedgerFailure.AppUpdateRequired(LedgerAppId.Solana, info.version, SolanaApp.MIN_APP_VERSION))
        }
        val app = SolanaApp(scope.session)
        val drafts = indices.associateWith { index ->
            val publicKey = app.getPublicKey(LedgerDerivation.solana(index), display = false)
            listOf(draft(Chain.Solana.Mainnet, Address.Type.Default, publicKey))
        }
        return AppKeys(drafts)
    }

    private suspend fun readBitcoin(scope: LedgerScope, indices: List<Int>): AppKeys {
        val info = scope.ensureApp(LedgerAppId.Bitcoin)
        if (!info.isAtLeast(BtcApp.MIN_APP_VERSION)) {
            throw LedgerFailureException(LedgerFailure.AppUpdateRequired(LedgerAppId.Bitcoin, info.version, BtcApp.MIN_APP_VERSION))
        }
        val app = BtcApp(scope.session)
        val fingerprint = app.getMasterFingerprint().toHexString()
        val bitcoin = mutableMapOf<Int, BitcoinKeys>()
        val drafts = indices.associateWith { index ->
            val accountXpub = app.getExtendedPublicKey(LedgerDerivation.bitcoinAccount(index), display = false)
            val receiveKey = app.getExtendedPublicKey(LedgerDerivation.bitcoinAddress(index, 0, 0), display = false).publicKey
            val drafted = draft(Chain.Bitcoin.Mainnet, Address.Type.BtcSegwit, receiveKey, extendedPublicKey = accountXpub.toZpub())
            if (drafted.displayAddress != BtcScripts.p2wpkhAddress(receiveKey)) {
                throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
            }
            bitcoin[index] = BitcoinKeys(fingerprint, accountXpub.encode())
            listOf(drafted)
        }
        return AppKeys(drafts, bitcoin)
    }

    private fun draft(chain: Chain, type: Address.Type, publicKey: ByteArray, extendedPublicKey: String = ""): HardwareAccountDraft =
        HardwareAccountDraft(
            chain = chain,
            addressType = type,
            displayAddress = LedgerAddresses.display(chain, publicKey),
            publicKeyHex = LedgerAddresses.hexWithPrefix(publicKey),
            extendedPublicKey = extendedPublicKey,
        )

    private fun LedgerFailure.isSkippable(): Boolean = this is LedgerFailure.AppNotInstalled ||
        this is LedgerFailure.UserRefused ||
        this is LedgerFailure.AppUpdateRequired

    private class AppKeys(
        val drafts: Map<Int, List<HardwareAccountDraft>>,
        val bitcoin: Map<Int, BitcoinKeys> = emptyMap(),
    )

    private class BitcoinKeys(val fingerprint: String, val accountXpub: String)

    private companion object {
        const val MAX_SEED_CHECKS = 3
    }
}
