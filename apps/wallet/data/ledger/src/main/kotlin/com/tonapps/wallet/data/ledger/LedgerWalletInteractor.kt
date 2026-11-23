package com.tonapps.wallet.data.ledger

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.data.ledger.discovery.LedgerImportCandidate
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor

interface LedgerWalletInteractor {

    suspend fun descriptor(walletId: String): HardwareWalletDescriptor

    suspend fun missingApps(walletId: String): List<LedgerChainApp>

    suspend fun missingChains(walletId: String): List<Chain>

    suspend fun rebind(walletId: String, device: LedgerDevice)

    suspend fun addMissingNetworks(walletId: String): LedgerAddNetworksResult

    suspend fun legacyWallet(walletId: String): LedgerLegacyWallet?

    suspend fun multichainTwinId(legacyWalletId: String): String?

    suspend fun prepareWalletSeeds(device: LedgerDevice, candidates: List<LedgerImportCandidate>)

    fun hasWalletSeed(candidate: LedgerImportCandidate): Boolean

    fun walletSeed(candidate: LedgerImportCandidate): ByteArray?

    fun forgetWalletSeeds()
}

data class LedgerAddNetworksResult(
    val added: List<Chain>,
    val skipped: Map<LedgerChainApp, LedgerFailure>,
)
