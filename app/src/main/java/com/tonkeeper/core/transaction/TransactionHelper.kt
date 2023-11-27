package com.tonkeeper.core.transaction

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.core.Coin
import io.tonapi.models.EmulateMessageToEventRequest
import io.tonapi.models.SendBlockchainMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Message
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.crypto.base64
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import ton.SendMode
import ton.contract.WalletV4R2Contract
import ton.wallet.Wallet
import kotlin.time.Duration.Companion.seconds

object TransactionHelper {

    private val walletApi = Tonapi.wallet
    private val emulationApi = Tonapi.emulation
    private val blockchainApi = Tonapi.blockchain

    suspend fun getFee(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        try {
            val cell = create(wallet, to, value, comment, max)
            val boc = base64(BagOfCells(cell).toByteArray())
            val request = EmulateMessageToEventRequest(boc)
            val response = emulationApi.emulateMessageToWallet(request)
            response.trace.transaction.totalFees
        } catch (e: Throwable) {
            0L
        }
    }

    suspend fun send(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val cell = create(wallet, to, value, comment, max)
        val boc = base64(BagOfCells(cell).toByteArray())
        val request = SendBlockchainMessageRequest(boc)
        blockchainApi.sendBlockchainMessage(request)
    }

    suspend fun create(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ): Cell {
        val transfer = build(
            destination = to,
            value = value,
            comment = comment,
            max = max,
        )

        val seqno = getSeqno(wallet.accountId)
        val walletId = WalletContract.DEFAULT_WALLET_ID

        val contract = wallet.contract
        val privateKey = App.walletManager.getPrivateKey(wallet.id)

        val message = WalletV4R2Contract.createTransferMessage(
            address = contract.address,
            stateInit = null,
            privateKey = privateKey,
            validUntil = (Clock.System.now() + 60.seconds).epochSeconds.toInt(),
            walletId = walletId,
            seqno = seqno,
            transfers = arrayOf(transfer)
        )

        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        return cell
    }

    private suspend fun getSeqno(
        accountId: String
    ): Int = withContext(Dispatchers.IO) {
        try {
            walletApi.getAccountSeqno(accountId).seqno
        } catch (e: Throwable) {
            0
        }
    }

    private fun build(
        destination: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ): WalletTransfer {
        return build(destination, Coin.toNano(value), comment, max)
    }

    private fun build(
        address: String,
        value: Long,
        comment: String? = null,
        max: Boolean = false
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.sendMode = if (max) {
            SendMode.CARRY_ALL_REMAINING_BALANCE.value
        } else {
            SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        }
        builder.destination = AddrStd.parse(address)
        builder.coins = Coins.of(value)
        builder.body = buildBody(comment)
        return builder.build()
    }

    private fun buildBody(text: String?): Cell? {
        if (text.isNullOrEmpty()) {
            return null
        }
        return buildCell {
            storeUInt(0, 32)
            storeBytes(text.toByteArray())
        }
    }
}