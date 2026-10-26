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
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.icu.Coins
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.requireHd
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.RawMessageEntity
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

class RelayerOutcomeUnknownException(cause: Throwable) : Exception(cause)

class RelayerQuoteExpiredException(cause: Throwable? = null) : Exception(cause)

class RelayerRejectedException(
    val reason: String?,
    val nothingSent: Boolean = false,
    cause: Throwable? = null,
) : Exception(reason, cause)

data class SwapMessagePayload(
    val to: String,
    val amount: BigInteger,
    val data: String?,
    val stateInit: String?,
    val excessesAddress: AddrStd? = null,
)

class GaslessSender(
    private val api: API,
    private val authorizationProvider: AuthorizationProvider,
    private val batteryRepository: BatteryRepository,
    private val pendingTransactionReporter: RemotePendingTransactionReporter,
    private val provider: ChainKitProvider,
    private val evmGasless: EvmGasless,
) {

    suspend fun sendEvm(
        wallet: WalletEntity,
        account: Account,
        signer: SigningWallet,
        option: TxFee,
        emulation: EvmGasless.Emulation,
    ) = withContext(Async.Io) {
        val auth = requireAuth(wallet)
        requireCharges(wallet, option)
        if (emulation.isExpired) {
            throw RelayerQuoteExpiredException()
        }

        // Relayer documents carry their own nonce and cost the sender no gas: neither argument is read.
        val payloads = emulation.operation.payloads.map { payload ->
            val signed = provider.signPayload(payload, account, option.fee, BigInteger.ZERO, signer.requireHd())
            signed.getOrNull()
                ?: throw IllegalStateException("Payload ${payload.id} was not signed", signed.error)
        }
        val operation = SignedOperation(emulation.operation.id, payloads)

        val response = try {
            broadcast { evmGasless.send(auth, operation) }
        } catch (e: BatteryClientException) {
            val rejection = EvmGasless.rejection(e)
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
            GaslessV2SendResponse.Status.failed -> throw RelayerRejectedException(reason = null)
            GaslessV2SendResponse.Status.unknown -> throw RelayerOutcomeUnknownException(
                IllegalStateException("Relayer answered `${response.status.value}` to ${operation.id}"),
            )
        }
    }

    suspend fun sendTron(
        wallet: WalletEntity,
        signer: SigningWallet,
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

        val signed = transaction.sign(tronPrivateKey(signer))

        broadcast {
            api.tron.sendWithBattery(
                transaction = signed,
                resources = resources,
                tronAddress = transfer.from,
                auth = auth,
            )
        }
        // Only a registered multichain wallet has pending transactions on the backend to report to.
        if (wallet.isMultichain) {
            pendingTransactionReporter.onTronTransactionSent(wallet.id, signed.txId)
        }
        batteryRepository.refreshBalanceDelay(wallet)
    }

    suspend fun sendJetton(
        wallet: WalletEntity,
        signer: SigningWallet,
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
        val privateKey = tonPrivateKey(signer)
        // Paying in the jetton itself repays the relayer with an extra gift in the same message, and
        // the forwarded TON is fixed — the emulated `extra` only prices the battery option.
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
                TransferEntity.BASE_FORWARD_AMOUNT
            } else {
                jettonForwardAmount(extra)
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
        signer: SigningWallet,
        option: TxFee,
        payloads: List<SwapMessagePayload>,
    ) = withContext(Async.Io) {
        val auth = requireAuth(wallet)
        requireCharges(wallet, option)

        val message = swapMessage(wallet, payloads)
        val privateKey = tonPrivateKey(signer)
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
        builder.bounceable = raw.addressTags
            .takeIf { it.userFriendly }
            ?.isBounceable
            ?: true
        builder.coins = raw.coins
        builder.sendMode = TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value
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
    // TON forwarded on top of the base amount, a zero extra falls back to 0.1 TON.
    private fun jettonForwardAmount(extra: Long): Coins = when {
        extra > 0L -> TransferEntity.BASE_FORWARD_AMOUNT
        extra == 0L -> TransferEntity.POINT_ONE_TON
        else -> Coins.of(abs(extra)) + TransferEntity.BASE_FORWARD_AMOUNT
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

    private suspend fun requireAuth(wallet: WalletEntity): Authorization {
        val auth = authorizationProvider.getAuthBy(wallet.id)
        if (auth.isEmpty) {
            throw IllegalStateException("Battery authorization is missing")
        }
        return auth
    }

    // Raw 32-byte secp256k1 scalar from the chainkit BIP44 m/44'/195' derivation; the unsigned
    // interpretation matters, a leading 0x80 byte would otherwise become a negative key.
    private fun tronPrivateKey(signer: SigningWallet): java.math.BigInteger {
        return java.math.BigInteger(1, signer.getPrivateKey(Chain.Tron.Mainnet).data())
    }

    private fun tonPrivateKey(signer: SigningWallet): PrivateKeyEd25519 {
        return PrivateKeyEd25519(signer.getPrivateKey(Chain.Ton.Mainnet).data())
    }

    private companion object {
        private const val VALID_UNTIL_OFFSET_SECONDS = 5 * 30L
    }
}
