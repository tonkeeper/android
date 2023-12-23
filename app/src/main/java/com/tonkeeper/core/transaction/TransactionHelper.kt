package com.tonkeeper.core.transaction

import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.address
import com.tonkeeper.api.toJSON
import com.tonkeeper.core.Coin
import com.tonkeeper.core.history.HistoryHelper
import io.tonapi.models.EmulateMessageToEventRequest
import io.tonapi.models.EmulateMessageToWalletRequest
import io.tonapi.models.JettonBalance
import io.tonapi.models.SendBlockchainMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Message
import org.ton.block.MsgAddressInt
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

    suspend fun emulateTon(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ): TransactionEmulate? = withContext(Dispatchers.IO) {
        try {
            val cell = createTon(wallet, to, value, comment, max)
            val boc = base64(BagOfCells(cell).toByteArray())
            val request = EmulateMessageToWalletRequest(boc)
            val response = emulationApi.emulateMessageToWallet(request)

            TransactionEmulate(
                fee = response.trace.transaction.totalFees,
                actions = HistoryHelper.mapping(wallet, response.event, false)
            )
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun emulateJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        to: String,
        value: Float,
        comment: String? = null,
    ): TransactionEmulate? = withContext(Dispatchers.IO) {
        try {
            val cell = createJetton(wallet, jetton, to, value, comment)
            val boc = base64(BagOfCells(cell).toByteArray())
            val request = EmulateMessageToWalletRequest(boc)
            val response = emulationApi.emulateMessageToWallet(request)

            TransactionEmulate(
                fee = response.trace.transaction.totalFees,
                actions = HistoryHelper.mapping(wallet, response.event, false)
            )
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun sendTon(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val cell = createTon(wallet, to, value, comment, max)
        val boc = base64(BagOfCells(cell).toByteArray())
        val request = SendBlockchainMessageRequest(boc)
        blockchainApi.sendBlockchainMessage(request)
    }

    suspend fun sendJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        to: String,
        value: Float,
        comment: String? = null,
    ) = withContext(Dispatchers.IO) {
        val cell = createJetton(wallet, jetton, to, value, comment)
        val boc = base64(BagOfCells(cell).toByteArray())
        val request = SendBlockchainMessageRequest(boc)
        blockchainApi.sendBlockchainMessage(request)
    }

    suspend fun createJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        to: String,
        value: Float,
        comment: String? = null,
    ): Cell {
        val transfer = buildJetton(
            wallet = wallet,
            jetton = jetton,
            destination = to,
            jettonValue = Coin.toNano(value),
            comment = comment
        )

        val seqno = getSeqno(wallet.accountId)
        val walletId = WalletContract.DEFAULT_WALLET_ID

        val privateKey = App.walletManager.getPrivateKey(wallet.id)

        val message = WalletV4R2Contract.createTransferMessage(
            address = wallet.contract.address,
            stateInit = if (seqno == 0) wallet.stateInit else null,
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

    suspend fun createTon(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ): Cell {
        val transfer = buildTon(
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
            stateInit = if (seqno == 0) wallet.stateInit else null,
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

    private fun buildJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        destination: String,
        jettonValue: Long,
        comment: String?
    ): WalletTransfer {
        val body = WalletV4R2Contract.createJettonBody(
            jettonAmount = jettonValue,
            toAddress = MsgAddressInt.parse(destination),
            responseAddress = wallet.contract.address,
            forwardAmount = 1,
            forwardPayload = null,
        )

        val builder = WalletTransferBuilder()
        builder.sendMode = SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        builder.bounceable = true
        builder.destination = AddrStd.parse(jetton.walletAddress.address)
        builder.coins = Coins.ofNano(Coin.toNano(0.64f))
        builder.body = body
        return builder.build()
    }

    private fun buildTon(
        destination: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ): WalletTransfer {
        val body = buildCommentBody(comment)
        return build(destination, Coin.toNano(value), max, body)
    }

    private fun build(
        address: String,
        value: Long,
        max: Boolean = false,
        body: Cell?
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.sendMode = if (max) {
            SendMode.CARRY_ALL_REMAINING_BALANCE.value
        } else {
            SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        }
        builder.destination = AddrStd.parse(address)
        builder.coins = Coins.ofNano(value)
        builder.body = body
        return builder.build()
    }

    private fun buildCommentBody(text: String?): Cell? {
        if (text.isNullOrEmpty()) {
            return null
        }
        return buildCell {
            storeUInt(0, 32)
            storeBytes(text.toByteArray())
        }
    }

}