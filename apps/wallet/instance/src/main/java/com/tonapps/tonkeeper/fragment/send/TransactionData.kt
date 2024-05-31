package com.tonapps.tonkeeper.fragment.send

import android.net.Uri
import com.tonapps.blockchain.Coins
import com.tonapps.extensions.toByteArray
import com.tonapps.security.Security
import com.tonapps.security.hex
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import ton.SendMode
import ton.transfer.Transfer
import java.math.BigInteger

data class TransactionData(
    val walletAddress: String? = null,
    val address: String? = null,
    val name: String? = null,
    val comment: String? = null,
    val amountRaw: String = "0",
    val max: Boolean = false,
    val token: AccountTokenEntity? = null,
    val bounce: Boolean = false,
    val encryptComment: Boolean = false,
    val nftAddress: String? = null
) {

    private val t: TokenEntity
        get() = token?.balance?.token ?: TokenEntity.TON

    val icon: Uri
        get() = t.imageUri

    val tokenName: String
        get() = t.name

    val tokenAddress: String
        get() = t.address

    val tokenSymbol: String
        get() = t.symbol

    val isTon: Boolean
        get() = t.isTon

    val isNft: Boolean
        get() = nftAddress != null

    val destination: AddrStd
        get() {
            if (isNft) {
                return AddrStd.parse(nftAddress!!)
            }
            if (isTon) {
                return AddrStd.parse(address!!)
            }
            return AddrStd.parse(token?.balance?.walletAddress!!)
        }

    val sendMode: Int
        get() {
            if (max && isTon) {
                return SendMode.CARRY_ALL_REMAINING_BALANCE.value
            }
            return SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        }

    val coins: Coins
        get() {
            if (isNft) {
                return Coins.of(0.064)
            }
            if (isTon) {
                return amount
            }
            return Coins.of(0.064)
        }

    val decimals: Int
        get() = t.decimals

    val amount: Coins
        get() {
            return Coins.of(amountRaw, decimals)
        }

    fun buildWalletTransfer(
        responseAddress: MsgAddressInt,
        stateInit: StateInit?,
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.bounceable = bounce
        builder.destination = destination
        builder.body = buildWalletTransferBody(responseAddress)
        builder.sendMode = sendMode
        builder.coins = coins.toTonLibCoin()
        builder.stateInit = stateInit
        return builder.build()
    }

    private fun buildWalletTransferBody(
        responseAddress: MsgAddressInt,
    ): Cell? {
        if (isNft) {
            return Transfer.nft(
                newOwnerAddress = MsgAddressInt.parse(address!!),
                excessesAddress = MsgAddressInt.parse(walletAddress!!),
                queryId = newWalletQueryId(),
                body = comment,
            )
        } else if (isTon) {
            return Transfer.text(comment)
        }
        return Transfer.jetton(
            coins = amount.toTonLibCoin(),
            toAddress = MsgAddressInt.parse(address!!),
            responseAddress = responseAddress,
            queryId = newWalletQueryId(),
            body = comment,
        )
    }

    private companion object {

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