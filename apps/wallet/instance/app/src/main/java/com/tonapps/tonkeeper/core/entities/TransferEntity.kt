package com.tonapps.tonkeeper.core.entities

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeStringTail
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.toByteArray
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledger.ton.TransactionBuilder
import com.tonapps.security.Security
import com.tonapps.security.hex
import com.tonapps.tonkeeper.extensions.toGrams
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.CommentEncryption
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder.Companion.beginCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
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

    private val stateInitRef: CellRef<StateInit>?
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

    private val coins: org.ton.block.Coins
        get() {
            return org.ton.block.Coins.ofNano(amount.toLong())
        }

    private fun getCommentForwardPayload(
        privateKey: PrivateKeyEd25519? = null
    ): Cell? {
        if (comment.isNullOrBlank()) {
            return null
        } else if (!commentEncrypted) {
            return beginCell()
                .storeUInt(0, 32)
                .storeStringTail(comment)
                .endCell()
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
        val body = body(privateKey, excessesAddress, jettonAmount) ?: Cell.empty()

        val builder = WalletTransferBuilder()
        builder.bounceable = bounceable
        builder.sendMode = sendMode
        if (isNft) {
            builder.coins = jettonTransferAmount.toGrams()
            builder.destination = AddrStd.parse(nftAddress!!)
        } else if (!isTon) {
            builder.coins = jettonTransferAmount.toGrams()
            builder.destination = AddrStd.parse(token.walletAddress)
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
    ): Array<WalletTransfer> {
        val gifts = mutableListOf<WalletTransfer>()
        gifts.add(
            getWalletTransfer(
                privateKey,
                excessesAddress,
                jettonAmount,
                jettonTransferAmount
            )
        )
        gifts.addAll(additionalGifts)
        return gifts.toTypedArray()
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
            ),
            internalMessage = internalMessage,
        )
    }

    fun getLedgerTransaction(jettonTransferAmount: Coins): Transaction? {
        if (wallet.type != Wallet.Type.Ledger) {
            return null
        }
        val builder = TransactionBuilder()
        if (isNft) {
            builder.setCoins(jettonTransferAmount.toGrams())
            builder.setDestination(AddrStd.parse(nftAddress!!))
            builder.setPayload(
                TonPayloadFormat.NftTransfer(
                    queryId = queryId,
                    newOwnerAddress = destination,
                    excessesAddress = contract.address,
                    forwardPayload = getCommentForwardPayload(),
                    forwardAmount = org.ton.block.Coins.ofNano(1L),
                    customPayload = null
                )
            )
        } else if (!isTon) {
            builder.setCoins(jettonTransferAmount.toGrams())
            builder.setDestination(AddrStd.parse(token.walletAddress))
            builder.setPayload(
                TonPayloadFormat.JettonTransfer(
                    queryId = queryId,
                    coins = coins,
                    receiverAddress = destination,
                    excessesAddress = contract.address,
                    forwardPayload = getCommentForwardPayload(),
                    forwardAmount = org.ton.block.Coins.ofNano(1L),
                    customPayload = null
                )
            )
        } else {
            builder.setCoins(coins)
            builder.setDestination(destination)
            if (comment != null) {
                builder.setPayload(TonPayloadFormat.Comment(comment))
            }
        }
        builder.setSendMode(sendMode)
        builder.setSeqno(seqno)
        builder.setTimeout(validUntil.toInt())
        builder.setBounceable(bounceable)
        builder.setStateInit(stateInitRef)
        return builder.build()
    }

    private fun body(privateKey: PrivateKeyEd25519?, excessesAddress: AddrStd, jettonAmount: Coins?): Cell? {
        if (isNft) {
            return nftBody(privateKey, excessesAddress)
        } else if (!isTon) {
            return jettonBody(privateKey, excessesAddress, jettonAmount)
        }
        return getCommentForwardPayload(privateKey)
    }

    private fun jettonBody(
        privateKey: PrivateKeyEd25519?,
        excessesAddress: AddrStd,
        jettonAmount: Coins?
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

    fun gaslessInternalGift(
        jettonAmount: Coins,
        batteryAddress: AddrStd
    ): WalletTransfer {
        if (isTon || isNft) {
            throw IllegalArgumentException("Gasless internal gift is not supported for TON and NFT transfers")
        }

        val body = TonTransferHelper.jetton(
            coins = jettonAmount.toGrams(),
            toAddress = batteryAddress,
            responseAddress = batteryAddress,
            queryId = queryId,
            forwardPayload = beginCell().storeOpCode(TONOpCode.GASLESS).endCell(),
            customPayload = tokenPayload?.customPayload,
        )


        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.messageData = MessageData.Raw(body, stateInitRef)
        builder.coins = POINT_ONE_TON.toGrams()
        builder.destination = AddrStd.parse(token.walletAddress)

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

        val BASE_FORWARD_AMOUNT = Coins.of(0.05, 9)
        val ONE_TON = Coins.ONE
        val POINT_ONE_TON = Coins.of(0.1, 9)

        fun newWalletQueryId(): BigInteger {
            return try {
                val tonkeeperSignature = 0x546de4ef.toByteArray(ByteOrder.LITTLE_ENDIAN)
                val randomBytes = Security.randomBytes(4)
                val value = tonkeeperSignature + randomBytes
                val hexString = hex(value)
                BigInteger(hexString, 16)
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                BigInteger.ZERO
            }
        }

        fun comment(text: String?): Cell? {
            if (text.isNullOrBlank()) {
                return null
            }
            return beginCell()
                .storeUInt(0, 32)
                .storeStringTail(text)
                .endCell()
        }
    }
}