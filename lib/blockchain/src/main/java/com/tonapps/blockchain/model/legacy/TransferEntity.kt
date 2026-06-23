package com.tonapps.blockchain.model.legacy

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.asCellRef
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.toByteArray
import com.tonapps.icu.Coins
import io.ktor.util.hex
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.crypto.SecureRandom
import org.ton.tlb.CellRef
import java.math.BigInteger
import java.nio.ByteOrder

data class TransferEntity(
    val wallet: WalletEntity,
    val token: BalanceEntity,
    val destination: AddrStd,
    val destinationPK: PublicKeyEd25519,
    val amount: Coins,
    val max: Boolean,
    val seqno: Int,
    val validUntil: Long,
    val bounceable: Boolean,
    val comment: String?,
    val nftAddress: String? = null,
    val commentEncrypted: Boolean,
    val queryId: BigInteger = newWalletQueryId(),
    val tokenPayload: TokenEntity.TransferPayload?
) {

    val contract: BaseWalletContract
        get() = wallet.contract

    val fakePrivateKey: PrivateKeyEd25519 by lazy {
        if (commentEncrypted) PrivateKeyEd25519() else EmptyPrivateKeyEd25519.invoke()
    }

    val isTon: Boolean
        get() = token.isTon

    val isNft: Boolean
        get() = nftAddress != null

    val stateInitRef: CellRef<StateInit>?
        get() {
            return if (0 >= seqno) {
                tokenPayload?.stateInit ?: contract.stateInitRef
            } else {
                tokenPayload?.stateInit
            }
        }

    val testnet: Boolean
        get() = wallet.testnet

    val sendMode: Int
        get() {
            return if (max && isTon) (TonSendMode.CARRY_ALL_REMAINING_BALANCE.value + TonSendMode.IGNORE_ERRORS.value) else (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        }

    val isValidComment: Boolean
        get() {
            if (wallet.isLedger && comment != null) {
                return comment.all { it.code in 32..126 }
            }

            return true
        }

    val coins: org.ton.block.Coins
        get() {
            return org.ton.block.Coins.ofNano(amount.toBigInteger())
        }

    fun getCommentForwardPayload(privateKey: PrivateKeyEd25519? = null): Cell? {
        if (comment.isNullOrBlank()) {
            return null
        } else if (!commentEncrypted) {
            return asCellRef(comment)
        } else {
            privateKey
                ?: throw IllegalArgumentException("Private key required for encrypted comment")
            return CommentEncryption.encryptComment(
                comment = comment,
                myPublicKey = privateKey.publicKey(),
                theirPublicKey = destinationPK,
                myPrivateKey = privateKey,
                senderAddress = contract.address.toAccountId()
            )
        }
    }

    private fun getWalletTransfer(
        privateKey: PrivateKeyEd25519?,
        excessesAddress: AddrStd,
        jettonAmount: Coins?,
        jettonTransferAmount: Coins,
    ): WalletTransfer {
        val body = body(privateKey, excessesAddress, jettonAmount) ?: Cell.Companion.empty()

        val builder = WalletTransferBuilder()
        builder.bounceable = bounceable
        builder.sendMode = sendMode
        if (isNft) {
            builder.coins = jettonTransferAmount.toGrams()
            builder.destination = AddrStd.Companion.parse(nftAddress!!)
        } else if (!isTon) {
            builder.coins = jettonTransferAmount.toGrams()
            builder.destination = AddrStd.Companion.parse(token.walletAddress)
        } else {
            builder.coins = coins
            builder.destination = destination
        }
        builder.messageData = MessageData.Raw(body, stateInitRef)
        return builder.build()
    }

    private fun getGifts(
        privateKey: PrivateKeyEd25519?,
        excessesAddress: AddrStd,
        additionalGifts: List<WalletTransfer>,
        jettonAmount: Coins?,
        jettonTransferAmount: Coins
    ): List<WalletTransfer> {
        val gifts = mutableListOf<WalletTransfer>()
        gifts.add(
            getWalletTransfer(
                privateKey, excessesAddress, jettonAmount, jettonTransferAmount
            )
        )
        gifts.addAll(additionalGifts)
        return gifts
    }

    fun getUnsignedBody(
        privateKey: PrivateKeyEd25519? = null,
        internalMessage: Boolean = false,
        excessesAddress: AddrStd? = null,
        additionalGifts: List<WalletTransfer> = emptyList(),
        jettonAmount: Coins? = null,
        jettonTransferAmount: Coins
    ): Cell {
        return contract.createTransferUnsignedBody(
            validUntil = validUntil,
            seqNo = seqno,
            gifts = getGifts(
                privateKey = privateKey,
                excessesAddress = excessesAddress ?: contract.address,
                additionalGifts = additionalGifts,
                jettonAmount = jettonAmount,
                jettonTransferAmount = jettonTransferAmount,
            ).toTypedArray(),
            internalMessage = internalMessage,
        )
    }

    private fun body(
        privateKey: PrivateKeyEd25519?, excessesAddress: AddrStd, jettonAmount: Coins?
    ): Cell? {
        if (isNft) {
            return nftBody(privateKey, excessesAddress)
        } else if (!isTon) {
            return jettonBody(privateKey, excessesAddress, jettonAmount)
        }
        return getCommentForwardPayload(privateKey)
    }

    private fun jettonBody(
        privateKey: PrivateKeyEd25519?, excessesAddress: AddrStd, jettonAmount: Coins?
    ): Cell {
        return TonTransferHelper.jetton(
            coins = jettonAmount?.toGrams() ?: coins,
            toAddress = destination,
            responseAddress = excessesAddress,
            queryId = queryId,
            forwardPayload = getCommentForwardPayload(privateKey),
            customPayload = tokenPayload?.customPayload,
        )
    }

    private fun nftBody(privateKey: PrivateKeyEd25519?, excessesAddress: AddrStd): Cell {
        return TonTransferHelper.nft(
            newOwnerAddress = destination,
            excessesAddress = excessesAddress,
            queryId = queryId,
            body = getCommentForwardPayload(privateKey),
        )
    }

    fun getEmulationBody(jettonTransferAmount: Coins): MessageBodyEntity {
        return MessageBodyEntity(
            wallet = wallet, seqNo = seqno, validUntil = validUntil, transfers = getGifts(
                privateKey = fakePrivateKey,
                excessesAddress = contract.address,
                jettonTransferAmount = jettonTransferAmount,
                additionalGifts = emptyList(),
                jettonAmount = null,
            )
        )
    }

    fun signForEstimation(
        jettonAmount: Coins? = null,
        internalMessage: Boolean,
        excessesAddress: AddrStd? = null,
        additionalGifts: List<WalletTransfer> = emptyList(),
        jettonTransferAmount: Coins
    ): Cell {
        return contract.createTransferMessageCell(
            address = contract.address,
            privateKey = fakePrivateKey,
            seqNo = seqno,
            unsignedBody = getUnsignedBody(
                privateKey = fakePrivateKey,
                internalMessage = internalMessage,
                excessesAddress = excessesAddress,
                additionalGifts = additionalGifts,
                jettonAmount = jettonAmount,
                jettonTransferAmount = jettonTransferAmount,
            ),
        )
    }

    fun sign(
        privateKey: PrivateKeyEd25519,
        jettonTransferAmount: Coins
    ): Cell {
        return contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKey,
            seqNo = seqno,
            unsignedBody = getUnsignedBody(
                privateKey = fakePrivateKey,
                jettonTransferAmount = jettonTransferAmount,
            ),
        )
    }

    fun gaslessInternalGift(
        jettonAmount: Coins, batteryAddress: AddrStd
    ): WalletTransfer {
        if (isTon || isNft) {
            throw IllegalArgumentException("Gasless internal gift is not supported for TON and NFT transfers")
        }

        val body = TonTransferHelper.jetton(
            coins = jettonAmount.toGrams(),
            toAddress = batteryAddress,
            responseAddress = batteryAddress,
            queryId = queryId,
            forwardPayload = CellBuilder.Companion.beginCell().storeOpCode(TONOpCode.GASLESS).endCell(),
            customPayload = tokenPayload?.customPayload,
        )


        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.messageData = MessageData.Raw(body, stateInitRef)
        builder.coins = POINT_ONE_TON.toGrams()
        builder.destination = AddrStd.Companion.parse(token.walletAddress)

        return builder.build()
    }

    class Builder(private val wallet: WalletEntity) {
        private var token: BalanceEntity? = null
        private var destination: AddrStd? = null
        private var destinationPK: PublicKeyEd25519? = null
        private var amount: Coins? = null
        private var max: Boolean = false
        private var seqno: Int? = null
        private var validUntil: Long? = null
        private var bounceable: Boolean = false
        private var comment: String? = null
        private var commentEncrypted: Boolean = false
        private var nftAddress: String? = null
        private var queryId: BigInteger? = null
        private var tokenPayload: TokenEntity.TransferPayload? = null

        fun setTokenPayload(tokenPayload: TokenEntity.TransferPayload) = apply {
            if (!tokenPayload.isEmpty) {
                this.tokenPayload = tokenPayload
            }
        }

        fun setQueryId(queryId: BigInteger) = apply { this.queryId = queryId }

        fun setNftAddress(nftAddress: String) = apply { this.nftAddress = nftAddress }

        fun setToken(token: BalanceEntity) = apply { this.token = token }

        fun setDestination(destination: AddrStd, destinationPK: PublicKeyEd25519) = apply {
            this.destination = destination
            this.destinationPK = destinationPK
        }

        fun setAmount(amount: Coins) = apply { this.amount = amount }

        fun setMax(max: Boolean = true) = apply { this.max = max }

        fun setSeqno(seqno: Int) = apply { this.seqno = seqno }

        fun setValidUntil(validUntil: Long) = apply { this.validUntil = validUntil }

        fun setBounceable(bounceable: Boolean = false) = apply { this.bounceable = bounceable }

        fun setComment(comment: String?, commentEncrypted: Boolean) = apply {
            this.comment = comment
            this.commentEncrypted = commentEncrypted
        }

        fun build(): TransferEntity {
            val token = token ?: throw IllegalArgumentException("Token is not set")
            val destination =
                destination ?: throw IllegalArgumentException("Destination is not set")
            val destinationPK =
                destinationPK ?: throw IllegalArgumentException("DestinationPK is not set")
            val amount = amount ?: throw IllegalArgumentException("Amount is not set")
            val seqno = seqno ?: throw IllegalArgumentException("Seqno is not set")
            val validUntil = validUntil ?: throw IllegalArgumentException("ValidUntil is not set")
            return TransferEntity(
                wallet = wallet,
                token = token,
                destination = destination,
                destinationPK = destinationPK,
                amount = amount,
                max = max,
                seqno = seqno,
                validUntil = validUntil,
                bounceable = bounceable,
                comment = comment,
                nftAddress = nftAddress,
                commentEncrypted = commentEncrypted,
                queryId = queryId ?: newWalletQueryId(),
                tokenPayload = tokenPayload
            )
        }
    }

    companion object {

        val BASE_FORWARD_AMOUNT = Coins.Companion.of(0.05, 9)
        val ONE_TON = Coins.Companion.ONE
        val POINT_ONE_TON = Coins.Companion.of(0.1, 9)

        fun newWalletQueryId(): BigInteger {
            return try {
                val tonkeeperSignature = 0x546de4ef.toByteArray(ByteOrder.LITTLE_ENDIAN)
                val randomBytes = SecureRandom.nextBytes(4)
                val value = tonkeeperSignature + randomBytes
                val hexString = hex(value)
                BigInteger(hexString, 16)
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                BigInteger.ZERO
            }
        }
    }
}
