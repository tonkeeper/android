package com.tonapps.tonkeeper.manager.migration

import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import com.tonapps.core.flags.WalletFeature
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.log.L
import com.tonapps.migration.data.MigrationRepository
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.tonkeeper.manager.ledger.LedgerLegacyTwinReplacer
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.battery.BatteryMigrationProof
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.LegacyWalletMigrator
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.settings.entities.PreferredTronFeeMethod
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * Moves the old-store wallets made from a BIP39 recovery phrase (not a TON one) into the multichain
 * store: the same phrase, so the TON address stays the same, and with it the dapp connections and
 * the push settings, which are kept by address. The old copy goes once the new one is registered.
 * Nothing moves while the LegacyBip39Migration flag is off.
 * The battery is kept by the key, not by the address, so its charges are moved with a request of their own.
 *
 * A phrase the multichain store already holds under another TON address (a V4R2 and a V5R1 wallet made
 * from the same phrase) moves only for the wallet that matches the multichain wallet's own address:
 * the other keeps its old copy, so that no wallet is swapped for one with a different address.
 */
class LegacyBip39WalletMigrator(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
    // Lazy: it brings the transaction manager with it, which the app start has no use for.
    private val migrationRepository: Lazy<MigrationRepository>,
    private val replacer: Lazy<LedgerLegacyTwinReplacer>,
    private val isEnabled: () -> Boolean = { WalletFeature.LegacyBip39Migration.isEnabled },
) : LegacyWalletMigrator {

    // With the flag off there is nothing to move, so the lock screen does not ask for the passcode.
    override suspend fun migratableWalletIds(): List<String> = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            return@withContext emptyList()
        }

        accountRepository.getWallets().filter { isCandidate(it) && isMovable(it) }.map { it.id }
    }

    override suspend fun migrate(
        walletIds: List<String>,
        coder: MnemonicCoder,
    ): List<String> = withContext(Dispatchers.IO + NonCancellable) {
        if (!isEnabled()) {
            return@withContext emptyList()
        }

        val wallets = accountRepository.getWallets().filter { it.id in walletIds }
        val moved = ArrayList<String>(wallets.size)
        for (legacy in wallets) {
            try {
                migrate(legacy, coder)
                moved += legacy.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                report("wallet migration failed", e)
            }
        }
        moved
    }

    private suspend fun migrate(legacy: WalletEntity, coder: MnemonicCoder) {
        val words = accountRepository.getMnemonic(legacy.id)?.toList()
            ?: error("No recovery phrase for the wallet")
        check(MnemonicHelper.isBip39Wallet(words, legacy.publicKey)) { "Not a BIP39 wallet" }

        var created = false
        val walletId = Mnemonic.from(MnemonicHelper.indexes(words)).use { mnemonic ->
            if (mcAccountRepository.walletExists(mnemonic)) {
                existingWalletId(legacy)
            } else {
                created = true
                mcAccountRepository.createWallet(
                    mnemonic = mnemonic,
                    mnemonicCoder = coder,
                    name = legacy.label.name,
                    emoji = legacy.label.emoji,
                    color = legacy.label.color,
                    tonWalletType = tonWalletType(legacy.version),
                ).id
            }
        } ?: error("The phrase is already in the multichain store under another TON address")

        // The old copy goes only for a wallet that holds the very same address: anything else would
        // swap the user's wallet for another one. A wallet made by this run is taken back, so it is
        // not left as a wallet the user never added.
        if (!holdsAddress(walletId, legacy)) {
            if (created) {
                mcAccountRepository.deleteWallet(walletId)
            }
            error("The multichain wallet has another TON address")
        }

        carryBackup(legacy.id, walletId)
        carrySettings(legacy.id, walletId)
        carryBattery(legacy, walletId)

        // The balances come with the sync the backend runs on registration.
        withTimeoutOrNull(SYNC_TIMEOUT_MS) { mcAccountRepository.awaitWalletSync(walletId) }

        check(replacer.value.replace(legacy, walletId, carryPush = true)) {
            "The old wallet could not be replaced"
        }
    }

    // A wallet backed up before is not asked for a backup again.
    private suspend fun carryBackup(legacyId: String, walletId: String) {
        // The stream only emits once the repository has loaded: a load that fails or hangs must not hold
        // the lock screen on the loader, so the backup is carried only when it is there in time.
        val backups = withTimeoutOrNull(BACKUP_TIMEOUT_MS) { backupRepository.stream.first() }
        if (backups == null) {
            report("wallet backup carry skipped", TimeoutException("The backup list did not load"))
            return
        }
        val backup = backups.filter { it.walletId == legacyId }.maxByOrNull { it.date } ?: return
        if (backups.any { it.walletId == walletId }) {
            return
        }

        // A record that cannot be written costs a backup reminder, not the move.
        try {
            backupRepository.addBackup(walletId, backup.source, backup.date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            report("wallet backup carry failed", e)
        }
    }

    // The battery charges sit with the old key's user, and only the old copy can sign and authorize for
    // it, so they move before that copy goes. Charges that do not move stay with the old key: the wallet
    // itself moves anyway, like on the migration screen.
    private suspend fun carryBattery(legacy: WalletEntity, walletId: String) {
        try {
            val privateKey = accountRepository.getPrivateKey(legacy.id)
                ?: error("No key for the wallet")
            val proof = BatteryMigrationProof.create(
                walletId = walletId,
                publicKey = legacy.publicKey.key.toByteArray(),
                sign = { privateKey.signToByteArray(it) },
            ) ?: error("The battery proof could not be built")

            // Not capped: the move waits for the old battery's pending messages and then always sends
            // the request, and cutting the wait short would leave the charges with a key that is gone.
            if (!migrationRepository.value.migrateBattery(legacy, walletId, proof)) {
                report("wallet battery carry failed", IllegalStateException("The battery did not move"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            report("wallet battery carry failed", e)
        }
    }

    // The settings kept per wallet that make sense for the new one: its place in the wallet list, the
    // hidden "finish setup" row and the preferred fee methods. The rest is keyed by something the
    // multichain wallet does not share (token ids, transaction ids, dapp hosts), or is gone with the
    // old store (safe mode).
    //
    // Each one is carried only where the new wallet says nothing of its own. So a wallet that was
    // already there keeps what the user set on it, and a retry after a run that stopped half-way
    // still carries what that run did not.
    private fun carrySettings(legacyId: String, walletId: String) {
        try {
            val sortIndex = settingsRepository.getWalletSortIndex(legacyId)
            if (sortIndex != null && settingsRepository.getWalletSortIndex(walletId) == null) {
                settingsRepository.setWalletSortIndex(walletId, sortIndex)
            }

            if (settingsRepository.isSetupHidden(legacyId)) {
                settingsRepository.setupHide(walletId)
            }

            val feeMethod = settingsRepository.getPreferredFeeMethod(legacyId)
            if (feeMethod != PreferredFeeMethod.UNSPECIFIED &&
                settingsRepository.getPreferredFeeMethod(walletId) == PreferredFeeMethod.UNSPECIFIED
            ) {
                settingsRepository.setPreferredFeeMethod(walletId, feeMethod)
            }

            val tronFeeMethod = settingsRepository.getPreferredTronFeeMethod(legacyId)
            if (tronFeeMethod != PreferredTronFeeMethod.UNSPECIFIED &&
                settingsRepository.getPreferredTronFeeMethod(walletId) == PreferredTronFeeMethod.UNSPECIFIED
            ) {
                settingsRepository.setPreferredTronFeeMethod(walletId, tronFeeMethod)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            report("wallet settings carry failed", e)
        }
    }

    // Logged and sent to the crash reports, neither of which the JVM tests have.
    private fun report(message: String, e: Throwable) {
        runCatching { L.e(e) }
        runCatching { AnalyticsHelper.Default.captureException(AnalyticException.Passcode(message, e)) }
    }

    private suspend fun holdsAddress(walletId: String, legacy: WalletEntity): Boolean {
        val address = mcAccountRepository.getTonAccount(walletId)?.displayAddress ?: return false
        return address.equalsAddress(legacy.address)
    }

    // Every multichain wallet also holds the other TON version as an account, so an address match
    // alone is not enough: the wallet's own primary TON address has to be the legacy one.
    private suspend fun existingWalletId(legacy: WalletEntity): String? {
        return mcAccountRepository.findWalletIdsByAddress(Chain.Ton.Mainnet, legacy.address)
            .firstOrNull { holdsAddress(it, legacy) }
    }

    // A BIP39 wallet whose phrase the multichain store does not already hold under another TON
    // address: that one could not be told from its twin, and would ask for the passcode on every start.
    private suspend fun isMovable(wallet: WalletEntity): Boolean {
        return try {
            val words = accountRepository.getMnemonic(wallet.id)?.toList() ?: return false
            if (!MnemonicHelper.isBip39Wallet(words, wallet.publicKey)) {
                return false
            }
            Mnemonic.from(MnemonicHelper.indexes(words)).use { mnemonic ->
                !mcAccountRepository.walletExists(mnemonic) || existingWalletId(wallet) != null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A phrase that cannot be read is left alone, but not silently.
            report("wallet migration check failed", e)
            false
        }
    }

    internal companion object {

        // An offline start must not hold the loader for long: the sync goes on without it.
        private const val SYNC_TIMEOUT_MS = 20_000L

        private const val BACKUP_TIMEOUT_MS = 5_000L

        // Only the versions the multichain wallet holds: any other would change the TON address.
        fun isCandidate(wallet: WalletEntity): Boolean {
            return wallet.type == WalletType.Default &&
                (wallet.version == WalletVersion.V4R2 || wallet.version == WalletVersion.V5R1)
        }

        fun tonWalletType(version: WalletVersion): Address.Type = when (version) {
            WalletVersion.V4R2 -> Address.Type.TonV4R2
            else -> Address.Type.TonV5R1
        }
    }
}
