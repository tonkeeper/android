package com.tonapps.wallet.data.ledger

import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.LedgerWallet
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.usb.LedgerUsbIds
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.reportingLedgerIssues
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractor
import com.tonapps.wallet.data.ledger.discovery.LedgerImportCandidate
import com.tonapps.wallet.data.ledger.signing.TonKeyReader
import com.tonapps.wallet.data.ledger.signing.TonLedgerSigner
import com.tonapps.wallet.data.ledger.signing.hexToBytes
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor
import java.util.concurrent.ConcurrentHashMap

internal class LedgerWalletInteractorImpl(
    private val controller: LedgerController,
    private val discovery: LedgerDiscoveryInteractor,
    private val accountRepository: McAccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
) : LedgerWalletInteractor {

    private val ton = TonLedgerSigner()
    private val walletSeeds = ConcurrentHashMap<String, ByteArray>()

    override suspend fun descriptor(walletId: String): HardwareWalletDescriptor = accountRepository.getHardwareDescriptor(walletId)
        ?: throw LedgerFailureException(LedgerFailure.Unexpected("Wallet is not paired with a Ledger"))

    override suspend fun missingApps(walletId: String): List<LedgerChainApp> {
        val chains = accountRepository.getCoinAccounts(walletId).map { it.chain }.toSet()
        return LedgerChainApp.entries.filter { app -> !app.isRequired && app.chains.any { it !in chains } }
    }

    override suspend fun missingChains(walletId: String): List<Chain> {
        val chains = accountRepository.getCoinAccounts(walletId).map { it.chain }.toSet()
        return missingApps(walletId).flatMap { it.chains }.filter { it !in chains }.distinct()
    }

    override suspend fun rebind(walletId: String, device: LedgerDevice) = reportingLedgerIssues(LedgerIssueSite.Wallet) {
        val descriptor = descriptor(walletId)
        val tonAccount = accountRepository.getCoinAccounts(walletId).firstOrNull { it.chain == Chain.Ton.Mainnet }
            ?: throw LedgerFailureException(LedgerFailure.Unexpected("TON is not added to this Ledger wallet"))
        val expectedPublicKey = tonAccount.publicKey.hexToBytes()
        val siblings = siblingsOf(walletId, descriptor.deviceId)
        val confirmedSiblings = controller.run(LedgerRequest(device.id, device.name, LedgerPurpose.Connect, walletId)) {
            val publicKey = TonKeyReader.readPublicKey(this, descriptor.accountIndex, display = false, expectedAddress = null)
            if (!publicKey.contentEquals(expectedPublicKey)) {
                throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Ton))
            }
            siblings.filter { sibling ->
                val siblingKey = TonKeyReader.readPublicKey(this, sibling.descriptor.accountIndex, display = false, expectedAddress = null)
                siblingKey.contentEquals(sibling.tonPublicKey)
            }
        }
        val bound = boundDevice(descriptor, device)
        val deviceModel = bound.model?.name ?: descriptor.deviceModel
        accountRepository.updateHardwareDescriptor(
            walletId,
            descriptor.copy(deviceId = bound.id, deviceName = bound.name, deviceModel = deviceModel),
        )
        for (sibling in confirmedSiblings) {
            accountRepository.updateHardwareDescriptor(
                sibling.walletId,
                sibling.descriptor.copy(deviceId = bound.id, deviceName = bound.name, deviceModel = deviceModel),
            )
        }
    }

    private fun boundDevice(descriptor: HardwareWalletDescriptor, device: LedgerDevice): LedgerDevice {
        val keepsBluetooth = LedgerUsbIds.isUsb(device.id) && !LedgerUsbIds.isUsb(descriptor.deviceId)
        return if (keepsBluetooth) {
            LedgerDevice(descriptor.deviceId, descriptor.deviceName, device.model ?: LedgerModel.fromName(descriptor.deviceModel))
        } else {
            device
        }
    }

    override suspend fun legacyWallet(walletId: String): LedgerLegacyWallet? {
        val wallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return null
        val ledger = wallet.ledger
        if (!LedgerLegacyTwins.isMigratable(wallet) || ledger == null) {
            return null
        }
        val publicKeyHex = wallet.publicKey.hex()
        return LedgerLegacyWallet(
            walletId = wallet.id,
            name = wallet.label.name,
            accountIndex = ledger.accountIndex,
            tonPublicKeyHex = publicKeyHex,
            tonAddress = LedgerAddresses.display(Chain.Ton.Mainnet, publicKeyHex.hexToBytes()),
        )
    }

    override suspend fun multichainTwinId(legacyWalletId: String): String? {
        val legacy = legacyWallet(legacyWalletId) ?: return null
        return accountRepository.findWalletIdsByAddress(Chain.Ton.Mainnet, legacy.tonAddress)
            .firstOrNull { walletId -> accountRepository.getWallet(walletId)?.isHardware == true }
    }

    override suspend fun prepareWalletSeeds(device: LedgerDevice, candidates: List<LedgerImportCandidate>) = reportingLedgerIssues(LedgerIssueSite.Wallet) {
        val targets = candidates.map { candidate ->
            val publicKeyHex = candidate.tonPublicKeyHex
                ?: throw LedgerFailureException(LedgerFailure.Unexpected("A Ledger wallet needs a TON account"))
            candidate.descriptor.accountIndex to publicKeyHex
        }
        val derived = linkedMapOf<String, ByteArray>()
        try {
            controller.run(LedgerRequest(device.id, device.name, LedgerPurpose.CreateWallet)) {
                targets.forEachIndexed { index, (accountIndex, publicKeyHex) ->
                    if (publicKeyHex in derived) {
                        return@forEachIndexed
                    }
                    val expectedPublicKey = publicKeyHex.hexToBytes()
                    val publicKey = TonKeyReader.readPublicKey(this, accountIndex, display = false, expectedAddress = null)
                    if (!publicKey.contentEquals(expectedPublicKey)) {
                        throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Ton))
                    }
                    val signature = ton.signWalletKeyProof(this, accountIndex, expectedPublicKey, step = index + 1, totalSteps = targets.size)
                    derived[publicKeyHex] = try {
                        LedgerWallet.fromProofSignature(expectedPublicKey, signature)
                            .getOrElse { throw LedgerFailureException(LedgerFailure.Unexpected("The TON app signed something other than the wallet key proof"), cause = it) }
                            .seed()
                    } finally {
                        signature.fill(0)
                    }
                }
            }
            derived.forEach { (publicKeyHex, seed) -> walletSeeds.put(publicKeyHex, seed)?.fill(0) }
            derived.clear()
        } finally {
            derived.values.forEach { it.fill(0) }
        }
    }

    override fun hasWalletSeed(candidate: LedgerImportCandidate): Boolean =
        candidate.tonPublicKeyHex?.let { walletSeeds.containsKey(it) } == true

    override fun walletSeed(candidate: LedgerImportCandidate): ByteArray? =
        candidate.tonPublicKeyHex?.let { walletSeeds[it]?.copyOf() }

    override fun forgetWalletSeeds() {
        val keys = walletSeeds.keys.toList()
        keys.forEach { walletSeeds.remove(it)?.fill(0) }
    }

    private suspend fun siblingsOf(walletId: String, deviceId: String): List<SiblingWallet> =
        accountRepository.getWallets()
            .filter { it.isHardware && it.id != walletId }
            .mapNotNull { wallet ->
                val siblingDescriptor = accountRepository.getHardwareDescriptor(wallet.id)
                val tonAccount = accountRepository.getCoinAccounts(wallet.id).firstOrNull { it.chain == Chain.Ton.Mainnet }
                if (siblingDescriptor == null || siblingDescriptor.deviceId != deviceId || tonAccount == null) {
                    null
                } else {
                    SiblingWallet(wallet.id, siblingDescriptor, tonAccount.publicKey.hexToBytes())
                }
            }

    private class SiblingWallet(
        val walletId: String,
        val descriptor: HardwareWalletDescriptor,
        val tonPublicKey: ByteArray,
    )

    override suspend fun addMissingNetworks(walletId: String): LedgerAddNetworksResult = reportingLedgerIssues(LedgerIssueSite.Wallet) {
        val descriptor = descriptor(walletId)
        val missing = missingApps(walletId)
        if (missing.isEmpty()) {
            return@reportingLedgerIssues LedgerAddNetworksResult(added = emptyList(), skipped = emptyMap())
        }
        val accounts = accountRepository.getCoinAccounts(walletId)
        val tonAddress = accounts.firstOrNull { it.chain == Chain.Ton.Mainnet }?.displayAddress
            ?: throw LedgerFailureException(LedgerFailure.Unexpected("TON is not added to this Ledger wallet"))
        val device = LedgerDevice(descriptor.deviceId, descriptor.deviceName, LedgerModel.fromName(descriptor.deviceModel))
        val result = discovery.discover(
            device = device,
            indices = listOf(descriptor.accountIndex),
            apps = listOf(LedgerChainApp.Ton) + missing,
            purpose = LedgerPurpose.AddAccounts,
            walletId = walletId,
            expectedTonAddress = tonAddress,
        )
        val discovered = result.accounts.single()
        val existingChains = accounts.map { it.chain }.toSet()
        val drafts = discovered.accounts.filter { it.chain !in existingChains }
        val skipped = result.skipped
        if (drafts.isEmpty()) {
            return@reportingLedgerIssues LedgerAddNetworksResult(added = emptyList(), skipped = skipped)
        }
        val merged = descriptor.copy(
            derivationPaths = descriptor.derivationPaths + discovered.descriptor(device).derivationPaths.filterKeys { it !in descriptor.derivationPaths },
            bitcoinMasterFingerprint = descriptor.bitcoinMasterFingerprint ?: discovered.bitcoinMasterFingerprint,
            bitcoinAccountXpub = descriptor.bitcoinAccountXpub ?: discovered.bitcoinAccountXpub,
        )
        accountRepository.addHardwareAccounts(walletId, merged, drafts)
        LedgerAddNetworksResult(added = drafts.map { it.chain }.distinct(), skipped = skipped)
    }
}
