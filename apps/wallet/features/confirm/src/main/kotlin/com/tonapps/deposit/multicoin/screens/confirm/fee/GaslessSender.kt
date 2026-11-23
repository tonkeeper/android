package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.MessageBodyEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.errors.SendBlockchainException
import com.tonapps.blockchain.ton.ExcessesAddressRewriter
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.cellFromHex
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.core.flags.BootFlags
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.FindingSeverity
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.severity
import com.tonapps.icu.Coins
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.requireHd
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.tx.RemotePendingTransactionReporter
import io.batteryapi.models.GaslessV2SendResponse
import io.infrastructure.ClientException as BatteryClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.tlb.asRef
import kotlin.math.abs

/** [txHash] is the transaction the relayer named, where it named one. */
class RelayerOutcomeUnknownException(cause: Throwable, val txHash: String? = null) : Exception(cause)

class RelayerQuoteExpiredException(cause: Throwable? = null) : Exception(cause)

class RelayerRejectedException(
    val reason: String?,
    val nothingSent: Boolean = false,
    cause: Throwable? = null,
    val txHash: String? = null,
) : Exception(reason, cause)

val Throwable.mayHaveRelayed: Boolean
    get() = this is RelayerOutcomeUnknownException || (this is RelayerRejectedException && !nothingSent)

/** The transaction a relayer named for a send that may have gone out; null where it named none. */
val Throwable.relayedHash: String?
    get() = when (this) {
        is RelayerOutcomeUnknownException -> txHash
        is RelayerRejectedException -> txHash.takeUnless { nothingSent }
        else -> null
    }?.takeIf { it.isNotBlank() }

data class SwapMessagePayload(
    val to: String,
    val amount: BigInteger,
    val data: String?,
    val stateInit: String?,
    val excessesAddress: AddrStd? = null,
    val bounce: Boolean? = null,
    // The send mode to sign; null for the battery's own, gas paid apart and errors ignored.
    val mode: Int? = null,
)

class GaslessSender(
    private val api: API,
    private val authorizationProvider: AuthorizationProvider,
    private val batteryRepository: BatteryRepository,
    private val pendingTransactionReporter: RemotePendingTransactionReporter,
    private val provider: ChainKitProvider,
    private val gaslessRelayer: GaslessRelayer,
    private val timeProvider: TimeProvider,
    private val ledgerSigner: LedgerSigningInteractor,
) {

    suspend fun sendOperation(
        wallet: WalletEntity,
        account: Account,
        signer: RelayerSigner,
        option: TxFee,
        emulation: GaslessRelayer.Emulation,
        calls: List<GaslessRelayer.Call>,
    ) = withContext(Async.Io) {
        val auth = requireAuth(wallet)
        requireCharges(wallet, option)
        if (emulation.isExpired(timeProvider.currentTimeMillis())) {
            throw RelayerQuoteExpiredException()
        }

        // A fee the relayer adds to a TON batch is held to the emulation's, not to the user's intent.
        requireSafe(emulation.intended, calls, account)

        val cryptoWallet = signer.requireSoftware().requireHd()
        // Relayer documents carry their own nonce and cost the sender no gas: neither argument is read.
        // The parts of a TON route go at consecutive seqnos: the relayer's where it states them.
        var chainSeqno: Int? = null
        val payloads = emulation.operation.payloads.mapIndexed { index, payload ->
            val seqno = suspend {
                emulation.tonSeqnos[payload.id]?.let(Math::toIntExact)
                    ?: ((chainSeqno ?: api.getAccountSeqno(wallet.accountId, wallet.network).also { chainSeqno = it }) + index)
            }

            when (val delivered = payload.meta) {
                is PayloadMeta.Ton.Main -> {
                    val asked = calls.getOrNull(index)?.ton
                        ?: throw IllegalStateException("Payload ${payload.id} answers no TON message asked for")
                    return@mapIndexed signDelivered(wallet, signer, payload, listOf(delivered), listOf(asked), emulation, seqno())
                }

                // The messages asked for, then the fee the relayer added and emulate held to the quote.
                is PayloadMeta.Ton.Batch -> {
                    val answered = delivered.calls.map { it as PayloadMeta.Ton.Main }
                    val asked = calls.map { it.ton ?: throw IllegalStateException("Payload ${payload.id} answers a call that is not TON") }
                    val signed = asked + answered.drop(asked.size)
                    return@mapIndexed signDelivered(wallet, signer, payload, answered, signed, emulation, seqno())
                }

                else -> Unit
            }

            val signed = provider.signPayload(payload, account, option.fee, BigInteger.ZERO, cryptoWallet)
            signed.getOrNull()
                ?: throw IllegalStateException("Payload ${payload.id} was not signed", signed.error)
        }
        val operation = SignedOperation(emulation.operation.id, payloads)

        val response = try {
            broadcast { gaslessRelayer.send(auth, operation) }
        } catch (e: BatteryClientException) {
            val rejection = GaslessRelayer.rejection(e)
            throw when {
                rejection.isExpired -> RelayerQuoteExpiredException(e)
                rejection.isAlreadySent -> RelayerOutcomeUnknownException(e)
                else -> RelayerRejectedException(
                    reason = rejection.message,
                    nothingSent = rejection.isNothingSent,
                    cause = e,
                )
            }
        }
        when (response.status) {
            GaslessV2SendResponse.Status.ok -> batteryRepository.refreshBalanceDelay(wallet)
            GaslessV2SendResponse.Status.failed -> throw RelayerRejectedException(reason = null, txHash = response.txHash)
            GaslessV2SendResponse.Status.unknown -> throw RelayerOutcomeUnknownException(
                IllegalStateException("Relayer answered `${response.status.value}` to ${operation.id}"),
                txHash = response.txHash,
            )
        }
        response.txHash?.takeIf { it.isNotBlank() }
    }

    /**
     * A transaction the relayer built is held to the call's intent before it is signed. What chainkit finds
     * dangerous in it (a payment elsewhere, more than the amount or the fee) refuses it unsigned, as does a
     * check that read nothing. The operation confirm shows the user these findings before the swipe; a
     * screen that shows none, the swap confirm, has only this. Validation switched off by the boot config checks
     * nothing here either. An EVM call has no intent: its relayer documents are typed, and
     * [GaslessRelayer.emulate] reads them.
     */
    private suspend fun requireSafe(relayed: Operation, calls: List<GaslessRelayer.Call>, account: Account) {
        if (!BootFlags.isTxValidationEnabled) {
            return
        }

        // The calls of one operation are held to one intent: the parts of a route to the sale as a whole.
        val intents = calls.mapNotNull { it.intent }.distinct()
        if (intents.isEmpty()) {
            return
        }

        val intent = intents.singleOrNull()
            ?: throw RelayerRejectedException("The calls of one operation answer ${intents.size} intents", nothingSent = true)
        // A validator that threw, or a chain with none, checked nothing.
        val refused = provider.validateOperation(relayed, account, intent)
            .findings
            .firstOrNull { it.severity == FindingSeverity.Danger || it is ValidationError.Unknown || it is ValidationError.UnsupportedChain }
        if (refused != null) {
            throw RelayerRejectedException(refused.message, nothingSent = true)
        }
    }

    /**
     * A TON message the relayer delivers inside one of its own is signed here, as the battery's messages
     * are: a request the wallet takes from another contract, at the wallet's seqno, good until the
     * relayer's deadline. Chainkit signs a `main` only as what the wallet sends and pays for itself.
     */
    private suspend fun signDelivered(
        wallet: WalletEntity,
        signer: RelayerSigner,
        payload: Payload,
        answered: List<PayloadMeta.Ton.Main>,
        messages: List<PayloadMeta.Ton.Main>,
        emulation: GaslessRelayer.Emulation,
        seqno: Int,
    ): SignedPayload {
        // Each message goes in the mode it was asked in, which the relayer's answer was held to.
        val modes = messages.map { it.mode ?: GaslessRelayer.DELIVERED_MODE }
        if (answered.map { it.mode ?: GaslessRelayer.DELIVERED_MODE } != modes || messages.any { it.token != null }) {
            throw IllegalStateException("Payload ${payload.id} is not a message the relayer delivers")
        }

        // The last line against a message after one that carries all, which emulate refuses first.
        if (modes.dropLast(1).any { GaslessRelayer.carriesAll(it) }) {
            throw IllegalStateException("Payload ${payload.id} sends a message after one that carries all")
        }

        // Good until the relayer's own deadline at the latest, and never long: a signed request can be
        // sent by whoever holds it until then.
        val now = api.getServerTime(wallet.network).toLong()
        val quoted = emulation.expiresAt / MILLIS_IN_SECOND
        val stated = (answered.mapNotNull { it.expireAt } + listOfNotNull(emulation.tonDeadlines[payload.id])).minOrNull() ?: quoted
        val validUntil = minOf(stated, quoted, now + MAX_DELIVERED_TTL_SECONDS)
        if (validUntil <= now) {
            throw RelayerQuoteExpiredException()
        }

        // What is signed is what the user confirmed, to the byte, and the fee emulate held to the quote.
        val transfers = messages.map { message ->
            swapTransfer(
                SwapMessagePayload(
                    to = message.to.display,
                    amount = message.value,
                    data = GaslessRelayer.deliveredBody(message),
                    stateInit = message.stateInit?.takeIf { it.isNotBlank() }?.cellFromBase64()?.hex(),
                    bounce = GaslessRelayer.deliveredBounce(message),
                    mode = message.mode ?: GaslessRelayer.DELIVERED_MODE,
                )
            )
        }
        val message = MessageBodyEntity(
            wallet = wallet,
            seqNo = seqno,
            validUntil = validUntil,
            transfers = transfers,
        )

        val boc = message.createSignedBody(tonPrivateKey(signer.requireSoftware()), internalMessage = true)
        return SignedPayload.signed(payload, boc.base64())
    }

    suspend fun sendTron(
        wallet: WalletEntity,
        signer: RelayerSigner,
        option: TxFee,
        transfer: TronTransfer,
        resources: TronResourcesEntity,
        nativeTransfer: Boolean = false,
    ) = withContext(Async.Io) {
        val auth = requireAuth(wallet)
        requireCharges(wallet, option)

        val transaction = if (nativeTransfer) {
            api.tron.buildNativeTransfer(
                from = transfer.from,
                to = transfer.to,
                amountSun = transfer.amount.toLong(),
            ).extendExpiration()
        } else {
            api.tron.buildSmartContractTransaction(transfer).extendExpiration()
        }

        val signed = when (signer) {
            is RelayerSigner.Software -> transaction.sign(tronPrivateKey(signer.wallet))
            is RelayerSigner.Ledger -> ledgerSigner.signTronTransaction(signer.walletId, transaction)
        }

        // A TRON transaction's id is fixed once it is signed: an unknown outcome is one under that id.
        try {
            broadcast {
                api.tron.sendWithBattery(
                    transaction = signed,
                    resources = resources,
                    tronAddress = transfer.from,
                    auth = auth,
                )
            }
        } catch (e: RelayerOutcomeUnknownException) {
            throw RelayerOutcomeUnknownException(e.cause ?: e, txHash = signed.txId)
        }
        // Only a registered multichain wallet has pending transactions on the backend to report to.
        if (wallet.isMultichain) {
            pendingTransactionReporter.onTronTransactionSent(wallet.id, signed.txId)
        }
        batteryRepository.refreshBalanceDelay(wallet)
        signed.txId
    }

    suspend fun sendJetton(
        wallet: WalletEntity,
        signer: RelayerSigner,
        option: TxFee,
        balance: BalanceEntity,
        excessesAddress: AddrStd,
        // Emulated `extra` the battery option was priced with; decides the forwarded TON amount.
        extra: Long,
        amount: BigInteger,
        to: String,
        comment: String?,
    ) = withContext(Async.Io) {
        val auth = requireAuth(wallet)
        requireCharges(wallet, option)

        val transfer = jettonTransfer(wallet, balance, amount, to, comment)
        val privateKey = tonPrivateKey(signer.requireSoftware())
        val transferCost = batteryRepository.getConfig(wallet.network).transferCost(balance.token.address)
        // Paying in the jetton itself repays the relayer with an extra gift in the same message, and
        // the forwarded TON is the transfer cost — the emulated `extra` only prices the battery option.
        val commission = gaslessCommission(option, balance)
        val unsignedBody = transfer.getUnsignedBody(
            privateKey = privateKey,
            internalMessage = true,
            excessesAddress = excessesAddress,
            additionalGifts = commission?.let {
                listOf(
                    transfer.gaslessInternalGift(
                        jettonAmount = it,
                        batteryAddress = excessesAddress,
                    )
                )
            } ?: emptyList(),
            jettonTransferAmount = if (commission != null) {
                transferCost
            } else {
                jettonForwardAmount(extra, transferCost)
            },
        )
        val boc = wallet.sign(
            privateKey = privateKey,
            seqNo = transfer.seqno,
            body = unsignedBody,
        )

        val keyPair = authorizationProvider.getWalletKeyPair(wallet.id)
        try {
            broadcast {
                api.sendToBlockchainWithBattery(
                    boc = boc.base64(),
                    auth = auth,
                    keyPair = keyPair,
                    network = wallet.network,
                    source = "multichain_send",
                    confirmationTime = 0.0,
                )
            }
        } finally {
            keyPair?.privateKey?.fill(0)
        }
        batteryRepository.refreshBalanceDelay(wallet)
    }

    suspend fun sendSwap(
        wallet: WalletEntity,
        signer: RelayerSigner,
        option: TxFee,
        payloads: List<SwapMessagePayload>,
    ) = withContext(Async.Io) {
        val auth = requireAuth(wallet)
        requireCharges(wallet, option)

        val message = swapMessage(wallet, payloads)
        val privateKey = tonPrivateKey(signer.requireSoftware())
        val boc = message.createSignedBody(privateKey, internalMessage = true)

        val keyPair = authorizationProvider.getWalletKeyPair(wallet.id)
        try {
            broadcast {
                api.sendToBlockchainWithBattery(
                    boc = boc.base64(),
                    auth = auth,
                    keyPair = keyPair,
                    network = wallet.network,
                    source = "swap",
                    confirmationTime = 0.0,
                )
            }
        } finally {
            keyPair?.privateKey?.fill(0)
        }
        batteryRepository.refreshBalanceDelay(wallet)
    }

    suspend fun swapMessage(
        wallet: WalletEntity,
        payloads: List<SwapMessagePayload>,
    ): MessageBodyEntity = withContext(Async.Io) {
        MessageBodyEntity(
            wallet = wallet,
            seqNo = api.getAccountSeqno(wallet.accountId, wallet.network),
            validUntil = api.getServerTime(wallet.network) + VALID_UNTIL_OFFSET_SECONDS,
            transfers = payloads.map(::swapTransfer),
        )
    }

    private fun swapTransfer(payload: SwapMessagePayload): WalletTransfer {
        val raw = RawMessageEntity(
            addressValue = payload.to,
            amount = payload.amount.toJavaBigInteger(),
            stateInitValue = null,
            payloadValue = payload.data,
        )

        val stateInit = payload.stateInit
            ?.takeIf { it.isNotBlank() }
            ?.cellFromHex()
            ?.asRef(StateInit)

        val body = payload.excessesAddress
            ?.let { ExcessesAddressRewriter.rewrite(raw.getPayload(), it) }
            ?: raw.getPayload()

        val builder = WalletTransferBuilder()
        builder.destination = raw.address
        builder.messageData = MessageData.Raw(body, stateInit, null)
        builder.bounceable = payload.bounce
            ?: raw.addressTags
                .takeIf { it.userFriendly }
                ?.isBounceable
            ?: true
        builder.coins = raw.coins
        builder.sendMode = payload.mode ?: (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        return builder.build()
    }

    // Shared with the estimation pass so the emulated transfer and the sent one are built the same way.
    // seqno/validUntil are read here rather than reused: the estimate can be minutes old.
    suspend fun jettonTransfer(
        wallet: WalletEntity,
        balance: BalanceEntity,
        amount: BigInteger,
        to: String,
        comment: String?,
    ): TransferEntity = withContext(Async.Io) {
        TransferEntity.Builder(wallet)
            .setToken(balance)
            .setAmount(Coins.ofNano(amount.toString(), balance.token.decimals))
            .setDestination(AddrStd.parse(to), EmptyPrivateKeyEd25519.publicKey())
            .setSeqno(api.getAccountSeqno(wallet.accountId, wallet.network))
            .setValidUntil(api.getServerTime(wallet.network) + VALID_UNTIL_OFFSET_SECONDS)
            .setBounceable(true)
            .setComment(comment, false)
            .build()
    }

    // Mirrors the legacy relayer send: a negative emulated extra means the relayer expects that much
    // TON forwarded on top of the battery config's transfer cost, a zero extra falls back to 0.1 TON.
    private fun jettonForwardAmount(extra: Long, transferCost: Coins): Coins = when {
        extra > 0L -> transferCost
        extra == 0L -> TransferEntity.POINT_ONE_TON
        else -> Coins.of(abs(extra)) + transferCost
    }

    // Non-null only for the gasless option: the fee is denominated in the jetton being sent.
    private fun gaslessCommission(option: TxFee, balance: BalanceEntity): Coins? {
        if (option.fee.asset !is Asset.Token) {
            return null
        }
        return Coins.ofNano(option.fee.amount.toString(), balance.token.decimals)
    }

    // The estimate can be minutes old by the time the user confirms; re-read the balance so the
    // relayer isn't asked to sponsor a transaction the wallet can no longer pay for.
    private suspend fun requireCharges(wallet: WalletEntity, option: TxFee) {
        if (!option.isBattery) {
            return
        }
        val charges = option.fee.amount
        val balance = BigInteger.fromInt(batteryRepository.getCharges(wallet, ignoreCache = true))
        if (charges > balance) {
            throw IllegalStateException("Not enough battery charges: $charges > $balance")
        }
    }

    // Anything that fails before the request leaves the device is a definite rejection and may be
    // retried; once the relayer has been asked to broadcast, an unanswered request must not re-arm the
    // slider, because a retry could double-send.
    private inline fun <T> broadcast(block: () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BatteryClientException) {
            throw e
        } catch (e: SendBlockchainException.SendBlockchainStatusException) {
            throw e
        } catch (e: Throwable) {
            val cause = (e as? SendBlockchainException.SendBlockchainErrorException)?.cause
            if (cause is BatteryClientException) {
                throw e
            }
            throw RelayerOutcomeUnknownException(e)
        }
    }

    private fun RelayerSigner.requireSoftware(): SigningWallet = when (this) {
        is RelayerSigner.Software -> wallet
        is RelayerSigner.Ledger -> throw LedgerFailureException(LedgerFailure.Unsupported("Relayed TON and EVM sends are not available for Ledger wallets"))
    }

    private suspend fun requireAuth(wallet: WalletEntity): Authorization {
        val auth = authorizationProvider.getAuthBy(wallet.id)
        if (auth.isEmpty) {
            throw IllegalStateException("Battery authorization is missing")
        }
        return auth
    }

    private fun tronPrivateKey(signer: SigningWallet): ByteArray {
        return signer.getPrivateKey(Chain.Tron.Mainnet).data()
    }

    private fun tonPrivateKey(signer: SigningWallet): PrivateKeyEd25519 {
        return PrivateKeyEd25519(signer.getPrivateKey(Chain.Ton.Mainnet).data())
    }

    private companion object {
        private const val VALID_UNTIL_OFFSET_SECONDS = 5 * 30L
        private const val MAX_DELIVERED_TTL_SECONDS = 10 * 60L
        private const val MILLIS_IN_SECOND = 1000L
    }
}
