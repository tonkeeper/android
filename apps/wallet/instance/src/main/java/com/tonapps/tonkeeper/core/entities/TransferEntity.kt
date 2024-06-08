package com.tonapps.tonkeeper.core.entities

import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.extensions.toByteArray
import com.tonapps.icu.Coins
import com.tonapps.security.Security
import com.tonapps.security.hex
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import java.math.BigInteger

data class TransferEntity(
    val wallet: WalletEntity,
    val token: BalanceEntity,
    val destination: AddrStd,
    val amount: Coins,
    val max: Boolean,
    val seqno: Int,
    val validUntil: Long,
    val bounceable: Boolean,
    val comment: String?,
    val nftAddress: String? = null
) {

    val contract: BaseWalletContract
        get() = wallet.contract

    val isTon: Boolean
        get() = token.isTon

    val isNft: Boolean
        get() = nftAddress != null

    val stateInit: StateInit?
        get() = if (seqno == 0) contract.stateInit else null

    val sendMode: Int
        get() {
            return if (max && isTon) TonSendMode.CARRY_ALL_REMAINING_BALANCE.value else (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        }

    private val coins: org.ton.block.Coins
        get() = org.ton.block.Coins.ofNano(amount.nano())


    private val gift: WalletTransfer by lazy {
        val builder = WalletTransferBuilder()
        builder.bounceable = bounceable
        builder.body = body()
        builder.sendMode = sendMode
        if (isNft) {
            builder.coins = coins
            builder.destination = AddrStd.parse(nftAddress!!)
        } else if (!isTon) {
            builder.coins = TRANSFER_PRICE
            builder.destination = AddrStd.parse(token.walletAddress)
        } else {
            builder.coins = coins
            builder.destination = destination
        }
        builder.stateInit = stateInit
        builder.build()
    }

    private val gifts: Array<WalletTransfer> by lazy {
        arrayOf(gift)
    }

    val unsignedBody: Cell by lazy {
        contract.createTransferUnsignedBody(
            validUntil = validUntil,
            seqno = seqno,
            gifts = gifts
        )
    }

    fun signedHash(privateKey: PrivateKeyEd25519): BitString {
        return BitString(privateKey.sign(unsignedBody.hash()))
    }

    fun messageBodyWithSign(signature: BitString): Cell {
        return contract.signedBody(signature, unsignedBody)
    }

    fun transferMessage(signature: BitString): Cell {
        val signedBody = messageBodyWithSign(signature)
        return contract.createTransferMessageCell(contract.address, seqno, signedBody)
    }

    private fun body(): Cell? {
        if (isNft) {
            return nftBody()
        } else if (!isTon) {
            return jettonBody()
        }
        return TonTransferHelper.text(comment)
    }

    private fun jettonBody(): Cell {
        return TonTransferHelper.jetton(
            coins = coins,
            toAddress = destination,
            responseAddress = contract.address,
            queryId = newWalletQueryId(),
            body = comment,
        )
    }

    private fun nftBody(): Cell {
        return TonTransferHelper.nft(
            newOwnerAddress = destination,
            excessesAddress = contract.address,
            queryId = newWalletQueryId(),
            body = comment,
        )
    }

    /*
    TonTransferHelper.nft(
                newOwnerAddress = MsgAddressInt.parse(address!!),
                excessesAddress = MsgAddressInt.parse(walletAddress!!),
                queryId = newWalletQueryId(),
                body = comment,
            )
     */

    fun toSignedMessage(privateKeyEd25519: PrivateKeyEd25519): Cell {
        return contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKeyEd25519,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
    }

    class Builder(private val wallet: WalletEntity) {
        private var token: BalanceEntity? = null
        private var destination: AddrStd? = null
        private var amount: Coins? = null
        private var max: Boolean = false
        private var seqno: Int? = null
        private var validUntil: Long? = null
        private var bounceable: Boolean = false
        private var comment: String? = null
        private var nftAddress: String? = null

        fun setNftAddress(nftAddress: String) = apply { this.nftAddress = nftAddress }

        fun setToken(token: BalanceEntity) = apply { this.token = token }

        fun setDestination(destination: AddrStd) = apply { this.destination = destination }

        fun setDestination(destination: String) = setDestination(AddrStd.parse(destination))

        fun setAmount(amount: Coins) = apply { this.amount = amount }

        fun setMax(max: Boolean = true) = apply { this.max = max }

        fun setSeqno(seqno: Int) = apply { this.seqno = seqno }

        fun setValidUntil(validUntil: Long) = apply { this.validUntil = validUntil }

        fun setBounceable(bounceable: Boolean = false) = apply { this.bounceable = bounceable }

        fun setComment(comment: String?) = apply { this.comment = comment }

        fun build(): TransferEntity {
            val token = token ?: throw IllegalArgumentException("Token is not set")
            val destination = destination ?: throw IllegalArgumentException("Destination is not set")
            val amount = amount ?: throw IllegalArgumentException("Amount is not set")
            val seqno = seqno ?: throw IllegalArgumentException("Seqno is not set")
            val validUntil = validUntil ?: throw IllegalArgumentException("ValidUntil is not set")
            return TransferEntity(
                wallet = wallet,
                token = token,
                destination = destination,
                amount = amount,
                max = max,
                seqno = seqno,
                validUntil = validUntil,
                bounceable = bounceable,
                comment = comment,
                nftAddress = nftAddress
            )
        }
    }

    companion object {

        private val TRANSFER_PRICE = org.ton.block.Coins.ofNano(Coins.of(0.064, 9).nano())

        fun newWalletQueryId(): BigInteger {
            return try {
                val tonkeeperSignature = 0x546de4ef.toByteArray()
                val randomBytes = Security.randomBytes(4)
                val value = tonkeeperSignature + randomBytes
                val hexString = hex(value)
                BigInteger(hexString, 16)
            } catch (e: Throwable) {
                BigInteger.ZERO
            }
        }
    }
}