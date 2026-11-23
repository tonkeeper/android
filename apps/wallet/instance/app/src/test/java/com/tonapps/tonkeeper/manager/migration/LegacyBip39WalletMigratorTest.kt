package com.tonapps.tonkeeper.manager.migration

import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.MnemonicType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.migration.data.MigrationRepository
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.tonkeeper.manager.ledger.LedgerLegacyTwinReplacer
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.settings.entities.PreferredTronFeeMethod
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBip39WalletMigratorTest {

    private val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        .split(" ")
    private val otherWords = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        .split(" ")

    private val bip39Key = MnemonicHelper.privateKey(words, MnemonicType.Bip39).publicKey()
    private val otherKey = MnemonicHelper.privateKey(otherWords, MnemonicType.Bip39).publicKey()

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val mcAccountRepository = mockk<McAccountRepository>(relaxed = true)
    private val backupsFlow = MutableSharedFlow<List<BackupEntity>>(replay = 1).also { it.tryEmit(emptyList()) }
    private val backupRepository = mockk<BackupRepository>(relaxed = true) {
        every { stream } returns backupsFlow
    }

    // A wallet says nothing of its own until a test sets it.
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { getWalletSortIndex(any()) } returns null
        every { isSetupHidden(any()) } returns false
        every { getPreferredFeeMethod(any()) } returns PreferredFeeMethod.UNSPECIFIED
        every { getPreferredTronFeeMethod(any()) } returns PreferredTronFeeMethod.UNSPECIFIED
    }
    private val migrationRepository = mockk<MigrationRepository>(relaxed = true) {
        coEvery { migrateBattery(any(), any(), any()) } returns true
    }
    private val replacer = mockk<LedgerLegacyTwinReplacer>(relaxed = true) {
        coEvery { replace(any(), any(), any()) } returns true
    }
    private val coder = mockk<MnemonicCoder>(relaxed = true)

    private var enabled = true

    private val migrator = LegacyBip39WalletMigrator(
        accountRepository,
        mcAccountRepository,
        backupRepository,
        settingsRepository,
        lazy { migrationRepository },
        lazy { replacer },
        isEnabled = { enabled },
    )

    // The failure of a wallet is logged, and the JVM has no Android log.
    @Before
    fun stubLog() {
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
    }

    @After
    fun unstubLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `only the default wallets of a version the multichain wallet holds are candidates`() {
        assertTrue(LegacyBip39WalletMigrator.isCandidate(wallet(version = WalletVersion.V5R1)))
        assertTrue(LegacyBip39WalletMigrator.isCandidate(wallet(version = WalletVersion.V4R2)))
        assertFalse(LegacyBip39WalletMigrator.isCandidate(wallet(version = WalletVersion.V3R2)))
        assertFalse(LegacyBip39WalletMigrator.isCandidate(wallet(type = WalletType.Watch)))
        assertFalse(LegacyBip39WalletMigrator.isCandidate(wallet(type = WalletType.Testnet)))
        assertFalse(LegacyBip39WalletMigrator.isCandidate(wallet(type = WalletType.Ledger)))
        assertFalse(LegacyBip39WalletMigrator.isCandidate(wallet(type = WalletType.Multichain)))
    }

    @Test
    fun `the TON wallet version carries over`() {
        assertEquals(Address.Type.TonV4R2, LegacyBip39WalletMigrator.tonWalletType(WalletVersion.V4R2))
        assertEquals(Address.Type.TonV5R1, LegacyBip39WalletMigrator.tonWalletType(WalletVersion.V5R1))
    }

    @Test
    fun `a wallet whose key is not the BIP39 key of its phrase is left alone`() = runBlocking {
        val bip39 = wallet(id = "bip39", publicKey = bip39Key)
        val tonPhrase = wallet(id = "ton", publicKey = otherKey)
        coEvery { accountRepository.getWallets() } returns listOf(bip39, tonPhrase)
        coEvery { accountRepository.getMnemonic(any()) } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false

        assertEquals(listOf("bip39"), migrator.migratableWalletIds())
    }

    @Test
    fun `a wallet without a readable phrase is not migratable`() = runBlocking {
        coEvery { accountRepository.getWallets() } returns listOf(wallet(publicKey = bip39Key))
        coEvery { accountRepository.getMnemonic(any()) } returns null

        assertTrue(migrator.migratableWalletIds().isEmpty())
    }

    @Test
    fun `a moved wallet keeps its name and version and replaces its old copy`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key, version = WalletVersion.V4R2)
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        holdsAddress("new", legacy)
        val type = slot<Address.Type>()
        coEvery {
            mcAccountRepository.createWallet(any(), coder, "Main", any(), any(), capture(type))
        } returns created

        val moved = migrator.migrate(listOf("old"), coder)

        assertEquals(listOf("old"), moved)
        assertEquals(Address.Type.TonV4R2, type.captured)
        coVerify { mcAccountRepository.awaitWalletSync("new") }
        coVerify { replacer.replace(legacy, "new", true) }
    }

    @Test
    fun `one wallet failing does not stop the others`() = runBlocking {
        val first = wallet(id = "first", publicKey = bip39Key)
        val second = wallet(id = "second", publicKey = bip39Key)
        coEvery { accountRepository.getWallets() } returns listOf(first, second)
        coEvery { accountRepository.getMnemonic("first") } returns null
        coEvery { accountRepository.getMnemonic("second") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        holdsAddress("new", second)
        coEvery { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) } returns created

        val moved = migrator.migrate(listOf("first", "second"), coder)

        assertEquals(listOf("second"), moved)
        coVerify(exactly = 0) { replacer.replace(first, any(), any()) }
    }

    @Test
    fun `a new wallet at another address never replaces the old one`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        coEvery { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) } returns created
        coEvery { mcAccountRepository.getTonAccount("new") } returns account("0:" + "1".repeat(64))

        val moved = migrator.migrate(listOf("old"), coder)

        assertTrue(moved.isEmpty())
        coVerify(exactly = 0) { replacer.replace(any(), any(), any()) }
        coVerify { mcAccountRepository.deleteWallet("new") }
    }

    @Test
    fun `a twin holding the address only as its other TON version is not the wallet`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key, version = WalletVersion.V4R2)
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns true
        coEvery { mcAccountRepository.findWalletIdsByAddress(any(), any()) } returns listOf("twin")
        coEvery { mcAccountRepository.getTonAccount("twin") } returns account("0:" + "2".repeat(64))

        assertTrue(migrator.migratableWalletIds().isEmpty())
        assertTrue(migrator.migrate(listOf("old"), coder).isEmpty())
        coVerify(exactly = 0) { replacer.replace(any(), any(), any()) }
        coVerify(exactly = 0) { mcAccountRepository.deleteWallet(any()) }
    }

    @Test
    fun `a twin at the same primary address takes the old wallet over`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns true
        coEvery { mcAccountRepository.findWalletIdsByAddress(any(), any()) } returns listOf("twin")
        holdsAddress("twin", legacy)

        assertEquals(listOf("old"), migrator.migratableWalletIds())
        every { settingsRepository.getWalletSortIndex("old") } returns 2
        every { settingsRepository.getWalletSortIndex("twin") } returns 5
        every { settingsRepository.getPreferredFeeMethod("old") } returns PreferredFeeMethod.BATTERY
        every { settingsRepository.getPreferredFeeMethod("twin") } returns PreferredFeeMethod.TON

        assertEquals(listOf("old"), migrator.migrate(listOf("old"), coder))
        coVerify(exactly = 0) { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) }
        coVerify { replacer.replace(legacy, "twin", true) }
        // The wallet that was already there keeps what its own settings say.
        verify(exactly = 0) { settingsRepository.setWalletSortIndex(any(), any()) }
        verify(exactly = 0) { settingsRepository.setPreferredFeeMethod(any(), any()) }
    }

    @Test
    fun `a retry after a run that stopped half-way still carries the settings`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        // The first run made the wallet and stopped before the settings.
        coEvery { mcAccountRepository.walletExists(any()) } returns true
        coEvery { mcAccountRepository.findWalletIdsByAddress(any(), any()) } returns listOf("new")
        holdsAddress("new", legacy)
        every { settingsRepository.getWalletSortIndex("old") } returns 2
        every { settingsRepository.isSetupHidden("old") } returns true
        every { settingsRepository.getPreferredFeeMethod("old") } returns PreferredFeeMethod.BATTERY
        every { settingsRepository.getPreferredTronFeeMethod("old") } returns PreferredTronFeeMethod.TRX

        assertEquals(listOf("old"), migrator.migrate(listOf("old"), coder))

        verify { settingsRepository.setWalletSortIndex("new", 2) }
        verify { settingsRepository.setupHide("new") }
        verify { settingsRepository.setPreferredFeeMethod("new", PreferredFeeMethod.BATTERY) }
        verify { settingsRepository.setPreferredTronFeeMethod("new", PreferredTronFeeMethod.TRX) }
    }

    @Test
    fun `a backup record that cannot be written does not stop the move`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        arrangeMove(legacy)
        backupsFlow.tryEmit(listOf(mockk<BackupEntity>(relaxed = true) { every { walletId } returns "old" }))
        every { backupRepository.addBackup(any(), any(), any()) } throws IllegalStateException("prefs")
        every { settingsRepository.getWalletSortIndex("old") } returns 2

        assertEquals(listOf("old"), migrator.migrate(listOf("old"), coder))
        verify { settingsRepository.setWalletSortIndex("new", 2) }
    }

    @Test
    fun `a failed replacement is not a moved wallet`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        holdsAddress("new", legacy)
        coEvery { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) } returns created
        coEvery { replacer.replace(any(), any(), any()) } returns false

        assertTrue(migrator.migrate(listOf("old"), coder).isEmpty())
    }

    @Test
    fun `the backup of the old wallet carries over`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        backupsFlow.tryEmit(listOf(BackupEntity(id = 1, walletId = "old", date = 1_000L)))
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        holdsAddress("new", legacy)
        coEvery { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) } returns created

        migrator.migrate(listOf("old"), coder)

        coVerify { backupRepository.addBackup("new", BackupEntity.Source.LOCAL, 1_000L) }
    }

    @Test
    fun `a backup list that never loads does not hold the migration`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        every { backupRepository.stream } returns MutableSharedFlow()
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic("old") } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        holdsAddress("new", legacy)
        coEvery { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) } returns created

        val moved = migrator.migrate(listOf("old"), coder)

        assertEquals(listOf("old"), moved)
        coVerify(exactly = 0) { backupRepository.addBackup(any(), any(), any()) }
    }

    private fun arrangeMove(legacy: WalletEntity) {
        coEvery { accountRepository.getWallets() } returns listOf(legacy)
        coEvery { accountRepository.getMnemonic(legacy.id) } returns words.toTypedArray()
        coEvery { mcAccountRepository.walletExists(any()) } returns false
        val created = mockk<McWalletEntity> { coEvery { id } returns "new" }
        holdsAddress("new", legacy)
        coEvery { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) } returns created
    }

    @Test
    fun `the place in the wallet list and the hidden setup row carry over`() = runBlocking {
        arrangeMove(wallet(id = "old", publicKey = bip39Key))
        every { settingsRepository.getWalletSortIndex("old") } returns 2
        every { settingsRepository.isSetupHidden("old") } returns true

        migrator.migrate(listOf("old"), coder)

        verify { settingsRepository.setWalletSortIndex("new", 2) }
        verify { settingsRepository.setupHide("new") }
    }

    @Test
    fun `a settings failure does not stop the move`() = runBlocking {
        arrangeMove(wallet(id = "old", publicKey = bip39Key))
        every { settingsRepository.getWalletSortIndex("old") } throws IllegalStateException("prefs")

        val moved = migrator.migrate(listOf("old"), coder)

        assertEquals(listOf("old"), moved)
    }

    @Test
    fun `the battery moves with a proof of the old key before the old copy goes`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        arrangeMove(legacy)
        coEvery { accountRepository.getPrivateKey("old") } returns MnemonicHelper.privateKey(words, MnemonicType.Bip39)
        val proof = slot<String>()
        coEvery { migrationRepository.migrateBattery(legacy, "new", capture(proof)) } returns true

        assertEquals(listOf("old"), migrator.migrate(listOf("old"), coder))

        assertTrue(Regex("[0-9a-f]{128}").matches(proof.captured))
        coVerifyOrder {
            migrationRepository.migrateBattery(legacy, "new", any())
            replacer.replace(legacy, "new", true)
        }
    }

    @Test
    fun `a battery that did not move does not stop the move`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        arrangeMove(legacy)
        coEvery { migrationRepository.migrateBattery(any(), any(), any()) } returns false

        assertEquals(listOf("old"), migrator.migrate(listOf("old"), coder))
        coVerify { replacer.replace(legacy, "new", true) }
    }

    @Test
    fun `a wallet without a readable key moves without its battery`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        arrangeMove(legacy)
        coEvery { accountRepository.getPrivateKey("old") } returns null

        assertEquals(listOf("old"), migrator.migrate(listOf("old"), coder))
        coVerify(exactly = 0) { migrationRepository.migrateBattery(any(), any(), any()) }
        coVerify { replacer.replace(legacy, "new", true) }
    }

    @Test
    fun `nothing is migratable and nothing moves while the flag is off`() = runBlocking {
        val legacy = wallet(id = "old", publicKey = bip39Key)
        arrangeMove(legacy)
        enabled = false

        assertTrue(migrator.migratableWalletIds().isEmpty())
        assertTrue(migrator.migrate(listOf("old"), coder).isEmpty())
        coVerify(exactly = 0) { mcAccountRepository.createWallet(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { replacer.replace(any(), any(), any()) }
    }

    private fun account(address: String) = mockk<AccountEntity> { every { displayAddress } returns address }

    private fun holdsAddress(walletId: String, legacy: WalletEntity) {
        coEvery { mcAccountRepository.getTonAccount(walletId) } returns account(legacy.address)
    }

    private fun wallet(
        id: String = "wallet",
        publicKey: org.ton.kotlin.crypto.PublicKeyEd25519 = bip39Key,
        type: WalletType = WalletType.Default,
        version: WalletVersion = WalletVersion.V5R1,
    ) = WalletEntity(
        id = id,
        publicKey = publicKey,
        type = type,
        version = version,
        label = Wallet.Label(accountName = "Main", emoji = "x", color = 0),
        initialized = true,
    )
}
