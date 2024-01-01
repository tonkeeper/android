package com.tonkeeper.core.transaction

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.core.Coin
import com.tonkeeper.core.history.HistoryHelper
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
        coins: Coins,
        comment: String? = null,
        max: Boolean = false,
        bounce: Boolean,
    ): TransactionEmulate? = withContext(Dispatchers.IO) {
        try {
            val cell = createTon(wallet, to, coins, comment, max, bounce)
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
        coins: Coins,
        comment: String? = null,
        bounce: Boolean,
    ): TransactionEmulate? = withContext(Dispatchers.IO) {
        try {
            val cell = createJetton(wallet, jetton, to, coins, comment, bounce)
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
        coins: Coins,
        comment: String? = null,
        max: Boolean = false,
        bounce: Boolean,
    ) = withContext(Dispatchers.IO) {
        val cell = createTon(wallet, to, coins, comment, max, bounce)
        val boc = base64(BagOfCells(cell).toByteArray())
        val request = SendBlockchainMessageRequest(boc)
        blockchainApi.sendBlockchainMessage(request)
    }

    suspend fun sendJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        to: String,
        coins: Coins,
        comment: String? = null,
        bounce: Boolean,
    ) = withContext(Dispatchers.IO) {
        val cell = createJetton(wallet, jetton, to, coins, comment, bounce)
        val boc = base64(BagOfCells(cell).toByteArray())
        val request = SendBlockchainMessageRequest(boc)
        blockchainApi.sendBlockchainMessage(request)
    }

    suspend fun createJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        to: String,
        coins: Coins,
        comment: String? = null,
        bounce: Boolean,
    ): Cell {
        val transfer = buildJetton(
            wallet = wallet,
            jetton = jetton,
            destination = to,
            jettonCoins = coins,
            comment = comment,
            bounce = bounce,
        )

        val seqno = getSeqno(wallet.accountId)
        val walletId = WalletContract.DEFAULT_WALLET_ID

        val privateKey = App.walletManager.getPrivateKey(wallet.id)

        val message = WalletV4R2Contract.createTransferMessage(
            address = wallet.contract.address,
            stateInit = if (seqno == 0) wallet.stateInit else null,
            privateKey = privateKey,
            validUntil = (Clock.System.now() + 60.seconds).epochSeconds,
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
        coins: Coins,
        comment: String? = null,
        max: Boolean = false,
        bounce: Boolean,
    ): Cell {
        val transfer = buildTon(
            destination = to,
            coins = coins,
            comment = comment,
            max = max,
            bounce = bounce,
        )

        val seqno = getSeqno(wallet.accountId)
        val walletId = WalletContract.DEFAULT_WALLET_ID

        val contract = wallet.contract
        val privateKey = App.walletManager.getPrivateKey(wallet.id)

        val message = WalletV4R2Contract.createTransferMessage(
            address = contract.address,
            stateInit = if (seqno == 0) wallet.stateInit else null,
            privateKey = privateKey,
            validUntil = (Clock.System.now() + 60.seconds).epochSeconds,
            walletId = walletId,
            seqno = seqno,
            transfers = arrayOf(transfer)
        )

        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        return cell
    }

    suspend fun getSeqno(
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
        jettonCoins: Coins,
        comment: String?,
        bounce: Boolean,
    ): WalletTransfer {
        val body = WalletV4R2Contract.createJettonBody(
            jettonCoins = jettonCoins,
            toAddress = MsgAddressInt.parse(destination),
            responseAddress = wallet.contract.address,
            forwardAmount = 1,
            forwardPayload = comment,
        )

        val builder = WalletTransferBuilder()
        builder.sendMode = SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        builder.bounceable = bounce
        builder.destination = AddrStd.parse(jetton.walletAddress.address)
        builder.coins = Coins.ofNano(Coin.toNano(0.64f))
        builder.body = body
        return builder.build()
    }

    private fun buildTon(
        destination: String,
        coins: Coins,
        comment: String? = null,
        max: Boolean = false,
        bounce: Boolean,
    ): WalletTransfer {
        val body = WalletV4R2Contract.buildCommentBody(comment)
        return build(destination, coins, max, body, bounce)
    }

    private fun build(
        address: String,
        coins: Coins,
        max: Boolean = false,
        body: Cell?,
        bounce: Boolean,
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.sendMode = if (max) {
            SendMode.CARRY_ALL_REMAINING_BALANCE.value
        } else {
            SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        }
        builder.bounceable = bounce
        builder.destination = AddrStd.parse(address)
        builder.coins = coins
        builder.body = body
        return builder.build()
    }

}