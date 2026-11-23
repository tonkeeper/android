package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.isRetryable
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TxConfirmLedgerMessageTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val ledgerSigner = mockk<LedgerSigningInteractor>()
    private val confirmResults = mockk<ConfirmResultListener>(relaxed = true)
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val sol = testAsset("sol/mainnet/coin", "SOL", "Solana", decimals = 9)

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun aSolanaMessageOnALedgerIsRefusedBeforeTheDeviceIsAsked() {
        coEvery { accountRepo.getWallet(any()) } returns ledgerWallet()

        val (feature, _) = open(ConfirmType.Message.Type.Default)

        val error = feature.confirmationError.value
        assertEquals(ConfirmationError.LedgerUnsupported, error)
        assertFalse(requireNotNull(error).isRetryable)
        assertNull(feature.retry())
        coVerify(exactly = 0) { ledgerSigner.signMessage(any(), any(), any(), any()) }
    }

    @Test
    fun aMessageTheLedgerRefusesWhileSigningIsNotOfferedAgain() {
        coEvery { accountRepo.getWallet(any()) } returns ledgerWallet()
        coEvery { ledgerSigner.signMessage(any(), any(), any(), any()) } throws
            LedgerFailureException(LedgerFailure.Unsupported("The transaction does not parse"))

        val (feature, shown) = open(ConfirmType.Message.Type.Transaction)
        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.LedgerUnsupported, feature.confirmationError.value)
        assertNull(feature.retry())
        coVerify(exactly = 1) { ledgerSigner.signMessage(any(), any(), any(), any()) }
    }

    @Test
    fun aSolanaTransactionOnALedgerIsOfferedForSigning() {
        coEvery { accountRepo.getWallet(any()) } returns ledgerWallet()

        val (feature, shown) = open(ConfirmType.Message.Type.Transaction)

        assertNotNull(shown.lastOrNull { it != null })
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aSolanaMessageOnASoftwareWalletIsOfferedForSigning() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()

        val (feature, _) = open(ConfirmType.Message.Type.Default)

        assertNull(feature.confirmationError.value)
    }

    private fun open(type: ConfirmType.Message.Type): Pair<TxConfirmFeature, List<PendingTransaction?>> {
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns testAccount(sol, address = SOLANA_ADDRESS)
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = sol.id, walletId = TestWallet.ID),
                type = ConfirmType.Message(data = MESSAGE, type = type),
                action = ConfirmAction.Sign,
                context = DAPP,
            ),
            provider = mockk<ChainKitProvider>(relaxed = true),
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            walletUnlocker = mockk<WalletUnlocker>(relaxed = true),
            signatureGate = SignatureGate { false },
            confirmResults = confirmResults,
            feeBuilder = mockk(relaxed = true),
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
            ledgerSigner = ledgerSigner,
        )
        store.put("tx-confirm", feature)

        val shown = CopyOnWriteArrayList<PendingTransaction?>()
        collection.launch { feature.pendingTx.collect { shown += it } }
        scheduler.runCurrent()
        return feature to shown
    }

    private fun ledgerWallet() = McWalletEntity(
        credentialId = CredentialIds.hardwareDescriptor(TestWallet.ID),
        name = "Ledger",
        id = TestWallet.ID,
        type = McWalletType.Multicoin,
    )

    private companion object {
        const val MESSAGE = "Ledger Solana hello"
        const val SOLANA_ADDRESS = "A7APrxHwRFnjiuGrYfD9T5qXr7tHGNZoquzuni6S1izt"
        val DAPP = ConfirmContext.Dapp(
            requestId = "request-1",
            topic = "topic-1",
            name = "Solana dApp",
            url = "solana.example.org",
        )
    }
}
