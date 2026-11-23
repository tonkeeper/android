package com.tonapps.wallet.data.ledger.signing

import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.ledger.LedgerAddresses
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.reportingLedgerIssues
import com.tonapps.wallet.data.ledger.solana.SolanaTrustedNames
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import org.ton.cell.Cell
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.tonapps.ledger.ton.Transaction as LedgerTonTransaction

internal class LedgerSigningInteractorImpl(
    private val controller: LedgerController,
    private val accountRepository: McAccountRepository,
    private val chainKitProvider: ChainKitProvider,
    private val tokenDescriptors: EvmTokenDescriptors,
    private val typedDataFilters: EvmTypedDataFilters,
    trustedNames: SolanaTrustedNames,
    timeProvider: TimeProvider,
) : LedgerSigningInteractor {

    private val evm = EvmLedgerSigner()
    private val tron = TronLedgerSigner()
    private val ton = TonLedgerSigner(clock = timeProvider::currentTimeMillis)
    private val bitcoin = BitcoinLedgerSigner(chainKitProvider)
    private val solana = SolanaLedgerSigner(trustedNames)

    override suspend fun signTransactions(walletId: String, requests: List<LedgerTransactionRequest>): List<ByteArray> = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        val prepared = requests.flatMap { request -> prepare(context, request) }
        controller.run(context.request(LedgerPurpose.SignTransaction)) {
            val verifier = IdentityVerifier(context)
            prepared.mapIndexed { index, item ->
                if (requests.any { it.signableUntil?.hasPassedNow() == true }) {
                    throw LedgerFailureException(LedgerFailure.RequestExpired)
                }
                verifier.verify(this, item.chain)
                signPrepared(this, context, item.withFreshSolanaInput(), step = index + 1, totalSteps = prepared.size)
            }
        }
    }

    override suspend fun signMessage(walletId: String, account: Account, type: MessageType, data: String): String = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        val chain = account.chain
        if (!chain.ledgerSignsMessage(type)) {
            throw LedgerFailureException(LedgerFailure.Unsupported("$type messages on ${chain.name}"))
        }
        val path = context.path(chain)
        val isTypedData = chain.isEvm && (type == MessageType.TypedMessage || type == MessageType.TypedMessageEip155)
        val clearSigning = if (isTypedData) { typedDataFilters.clearSigning(data) } else { null }
        controller.run(context.request(LedgerPurpose.SignMessage)) {
            IdentityVerifier(context).verify(this, chain)
            when {
                isTypedData -> evm.signTypedData(this, path, data, clearSigning)
                chain.isEvm && (type == MessageType.Legacy || type == MessageType.LegacyEip155 || type == MessageType.Default) ->
                    evm.signPersonalMessage(this, path, data)
                chain is Chain.Tron && type == MessageType.Transaction -> tron.signJsonTransaction(this, path, data)
                chain is Chain.Tron && type == MessageType.Default -> tron.signPersonalMessage(this, path, data)
                chain is Chain.Solana && type == MessageType.Transaction ->
                    solana.signWireTransaction(this, path, context.publicKey(chain), data)
                else -> throw LedgerFailureException(LedgerFailure.Unsupported("$type messages on ${chain.name}"))
            }
        }
    }

    override suspend fun signTonTransactions(walletId: String, transactions: List<LedgerTonTransaction>): List<Cell> = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        controller.run(context.request(LedgerPurpose.SignTransaction)) {
            IdentityVerifier(context).verify(this, Chain.Ton.Mainnet)
            transactions.mapIndexed { index, transaction ->
                ton.signTransaction(this, context.accountIndex, transaction, step = index + 1, totalSteps = transactions.size)
            }
        }
    }

    override suspend fun signTonTransaction(
        walletId: String,
        transaction: LedgerTonTransaction,
        step: Int,
        totalSteps: Int,
    ): Cell = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        controller.run(context.request(LedgerPurpose.SignTransaction)) {
            IdentityVerifier(context).verify(this, Chain.Ton.Mainnet)
            ton.signTransaction(this, context.accountIndex, transaction, step, totalSteps)
        }
    }

    override suspend fun signTonProof(walletId: String, domain: String, timestamp: Long, payload: String): ByteArray = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        controller.run(context.request(LedgerPurpose.SignProof)) {
            IdentityVerifier(context).verify(this, Chain.Ton.Mainnet)
            ton.signProof(this, context.accountIndex, context.tonPublicKey(), domain, timestamp, payload)
        }
    }

    override suspend fun signTronTransaction(walletId: String, transaction: TronTransaction): TronTransaction = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        val path = context.path(Chain.Tron.Mainnet)
        val signature = controller.run(context.request(LedgerPurpose.SignTransaction)) {
            IdentityVerifier(context).verify(this, Chain.Tron.Mainnet)
            tron.signRawData(this, path, transaction.rawDataBytes)
        }
        transaction.withSignature(signature)
    }

    override suspend fun signSolanaTransactions(walletId: String, account: Account, transactions: List<String>): List<String> = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        val chain = account.chain
        if (chain !is Chain.Solana) {
            throw LedgerFailureException(LedgerFailure.Unsupported("Batch signing on ${chain.name}"))
        }
        val path = context.path(chain)
        controller.run(context.request(LedgerPurpose.SignTransaction)) {
            IdentityVerifier(context).verify(this, chain)
            transactions.mapIndexed { index, transaction ->
                solana.signWireTransaction(this, path, context.publicKey(chain), transaction, step = index + 1, totalSteps = transactions.size)
            }
        }
    }

    override suspend fun verifyAddress(walletId: String, chain: Chain) = signing {
        val context = LedgerWalletContext.load(accountRepository, walletId)
        val account = context.account(chain)
        controller.run(context.request(LedgerPurpose.VerifyAddress)) {
            val path = context.path(chain)
            when {
                chain is Chain.Ton -> {
                    val publicKey = TonKeyReader.readPublicKey(this, context.accountIndex, display = true, expectedAddress = account.displayAddress)
                    requireMatch(publicKey.contentEquals(context.tonPublicKey()), LedgerAppId.Ton)
                }
                chain.isEvm -> {
                    val chainId = chain.caip2.substringAfter(EIP155_PREFIX, "").toLongOrNull()
                    val address = evm.readAddress(this, path, display = true, expectedAddress = account.displayAddress, chainId = chainId)
                    requireMatch(address.equals(account.displayAddress, ignoreCase = true), LedgerAppId.Ethereum)
                }
                chain is Chain.Tron -> {
                    val address = tron.readAddress(this, path, display = true, expectedAddress = account.displayAddress)
                    requireMatch(address == account.displayAddress, LedgerAppId.Tron)
                }
                chain is Chain.Solana -> {
                    val publicKey = solana.readPublicKey(this, path, display = true, expectedAddress = account.displayAddress)
                    requireMatch(LedgerAddresses.display(chain, publicKey) == account.displayAddress, LedgerAppId.Solana)
                }
                chain is Chain.Bitcoin -> bitcoin.verifyReceiveAddress(
                    scope = this,
                    accountIndex = context.accountIndex,
                    expectedFingerprint = context.descriptor.bitcoinMasterFingerprint,
                    expectedZpub = account.segwitPublicKey,
                    expectedAddress = account.displayAddress,
                )
                else -> throw LedgerFailureException(LedgerFailure.Unsupported("Address verification on ${chain.name}"))
            }
        }
    }

    private suspend fun prepare(context: LedgerWalletContext, request: LedgerTransactionRequest): List<PreparedTransaction> {
        val chain = request.transaction.account.chain
        val providerPayload = (request.transaction as? Transaction.Swap)?.data?.takeIf { it.isNotBlank() }
        if (providerPayload != null && chain is Chain.Tron) {
            return listOf(PreparedTransaction(chain, ByteArray(0), bitcoinSpend = null, tronJson = providerPayload))
        }
        if (providerPayload != null && chain is Chain.Solana) {
            return listOf(PreparedTransaction(chain, ByteArray(0), bitcoinSpend = null, solanaPrebuilt = providerPayload))
        }
        if (providerPayload != null && chain is Chain.Bitcoin) {
            throw LedgerFailureException(LedgerFailure.Unsupported("Bitcoin swaps with a provider PSBT"))
        }
        return buildInputs(request).mapIndexed { inputIndex, input ->
            val bitcoinSpend = if (chain is Chain.Bitcoin) {
                bitcoin.plan(request.transaction.account, context.accountIndex, input)
            } else {
                null
            }
            val tokenDescriptor = if (chain.isEvm) {
                tokenDescriptors.forTransaction(input)
            } else {
                null
            }
            val jettonMaster = if (chain is Chain.Ton) {
                ((request.transaction as? Transaction.Transfer)?.mainAsset as? Asset.Token)?.contract?.display
            } else {
                null
            }
            PreparedTransaction(
                chain = chain,
                input = input,
                bitcoinSpend = bitcoinSpend,
                tokenDescriptor = tokenDescriptor,
                jettonMaster = jettonMaster,
                rebuild = SolanaRebuild(request, inputIndex).takeIf { chain is Chain.Solana },
            )
        }
    }

    private suspend fun buildInputs(request: LedgerTransactionRequest): List<ByteArray> {
        val inputs = chainKitProvider.buildUnsignedInputs(request.transaction, request.fee, request.nonce)
        return inputs.getOrNull()
            ?: throw LedgerFailureException(LedgerFailure.InvalidPayload(0, inputs.error?.toString() ?: "Unable to build the transaction"), cause = inputs.error)
    }

    private suspend fun PreparedTransaction.withFreshSolanaInput(): PreparedTransaction {
        val rebuild = rebuild ?: return this
        val input = buildInputs(rebuild.request).getOrNull(rebuild.inputIndex)
            ?: throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The transaction changed while the Ledger was connecting"))
        return PreparedTransaction(chain, input, bitcoinSpend, tronJson, tokenDescriptor, jettonMaster)
    }

    private suspend fun signPrepared(
        scope: LedgerScope,
        context: LedgerWalletContext,
        item: PreparedTransaction,
        step: Int,
        totalSteps: Int,
    ): ByteArray {
        val chain = item.chain
        return when {
            chain.isEvm -> evm.signTransaction(scope, context.path(chain), chain, item.input, item.tokenDescriptor, step, totalSteps)
            chain is Chain.Tron && item.tronJson != null -> tron.signProviderTransaction(scope, context.path(chain), item.tronJson, step, totalSteps)
            chain is Chain.Tron -> tron.signTransaction(scope, context.path(chain), item.input, step, totalSteps)
            chain is Chain.Ton -> ton.signChainkitInput(
                scope = scope,
                accountIndex = context.accountIndex,
                publicKey = context.tonPublicKey(),
                input = item.input,
                waitedSeconds = item.preparedAt.elapsedNow().inWholeSeconds,
                step = step,
                totalSteps = totalSteps,
                jettonMaster = item.jettonMaster,
            )
            chain is Chain.Solana && item.solanaPrebuilt != null ->
                solana.signPrebuilt(scope, context.path(chain), context.publicKey(chain), item.solanaPrebuilt, step, totalSteps)
            chain is Chain.Solana -> solana.signChainkitInput(scope, context.path(chain), context.publicKey(chain), item.input, step, totalSteps)
            chain is Chain.Bitcoin -> bitcoin.sign(
                scope = scope,
                spend = requireNotNull(item.bitcoinSpend),
                accountIndex = context.accountIndex,
                expectedFingerprint = context.descriptor.bitcoinMasterFingerprint,
                expectedZpub = context.account(chain).segwitPublicKey,
                step = step,
                totalSteps = totalSteps,
            )
            else -> throw LedgerFailureException(LedgerFailure.Unsupported("Signing on ${chain.name}"))
        }
    }

    private inline fun <T> signing(block: () -> T): T = reportingLedgerIssues(LedgerIssueSite.Signing, block)

    private fun requireMatch(matches: Boolean, app: LedgerAppId) {
        if (!matches) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(app))
        }
    }

    private class PreparedTransaction(
        val chain: Chain,
        val input: ByteArray,
        val bitcoinSpend: BitcoinLedgerSigner.PlannedSpend?,
        val tronJson: String? = null,
        val tokenDescriptor: ByteArray? = null,
        val jettonMaster: String? = null,
        val rebuild: SolanaRebuild? = null,
        val solanaPrebuilt: String? = null,
    ) {
        val preparedAt: TimeMark = TimeSource.Monotonic.markNow()
    }

    private class SolanaRebuild(val request: LedgerTransactionRequest, val inputIndex: Int)

    private inner class IdentityVerifier(private val context: LedgerWalletContext) {

        private val verified = mutableSetOf<LedgerChainApp>()
        private var lockEvents = -1

        suspend fun verify(scope: LedgerScope, chain: Chain) {
            val app = LedgerChainApp.forChain(chain)
            if (lockEvents != scope.session.lockEvents) {
                verified.clear()
                lockEvents = scope.session.lockEvents
            }
            if (!verified.add(app)) {
                return
            }
            val account = context.account(chain)
            when (app) {
                LedgerChainApp.Ton -> {
                    val publicKey = TonKeyReader.readPublicKey(scope, context.accountIndex, display = false, expectedAddress = null)
                    requireMatch(publicKey.contentEquals(context.tonPublicKey()), LedgerAppId.Ton)
                }
                LedgerChainApp.Ethereum -> {
                    val address = evm.readAddress(scope, context.path(chain), display = false, expectedAddress = null)
                    requireMatch(address.equals(account.displayAddress, ignoreCase = true), LedgerAppId.Ethereum)
                }
                LedgerChainApp.Tron -> {
                    val address = tron.readAddress(scope, context.path(chain), display = false, expectedAddress = null)
                    requireMatch(address == account.displayAddress, LedgerAppId.Tron)
                }
                LedgerChainApp.Solana -> {
                    val publicKey = solana.readPublicKey(scope, context.path(chain), display = false, expectedAddress = null)
                    requireMatch(publicKey.contentEquals(context.publicKey(chain)), LedgerAppId.Solana)
                }
                LedgerChainApp.Bitcoin -> Unit
            }
        }
    }
}

private const val EIP155_PREFIX = "eip155:"
