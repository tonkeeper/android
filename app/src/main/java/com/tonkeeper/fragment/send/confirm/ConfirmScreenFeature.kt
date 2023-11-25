package com.tonkeeper.fragment.send.confirm

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.shortAddress
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import com.tonkeeper.fragment.send.SendScreenFeature
import core.extensions.toBase64
import io.tonapi.models.EmulateMessageToEventRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Message
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletTransfer
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.contract.WalletV4R2Contract
import uikit.mvi.UiFeature

class ConfirmScreenFeature: UiFeature<ConfirmScreenState, ConfirmScreenEffect>(ConfirmScreenState()) {

    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val emulationApi = Tonapi.emulation

    private var destinationAddress: String = ""
    private var amountValue = 0f
    private var comment: String? = null

    fun send() {
        viewModelScope.launch {
            transferMessage()
        }
    }

    private suspend fun transferMessage() = withContext(Dispatchers.IO) {
        if (true) {
            return@withContext
        }
        Log.d("ConfirmLog", "start")
        try {
            val walletManager = App.walletManager
            val wallet = walletManager.getWalletInfo()!!
            val liteClient = walletManager.liteClient!!

            val accountInfo = walletManager.getAccount(wallet.accountId)!!
            val contract = WalletV4R2Contract(accountInfo)

            val transfer = WalletTransfer {
                destination = AddrStd.parse(destinationAddress)
                coins = Coins.of(amountValue.toDouble())
            }

            /*contract.transfer(
                liteClient.liteApi,
                wallet.privateKey,
                Instant.DISTANT_FUTURE,
                transfer
            )*/

            Log.d("ConfirmLog", "done!")

            /*

            val message = contract.createTransferMessage(
                wallet.privateKey,
                Instant.DISTANT_FUTURE,
                transfer
            )


            val cell = buildCell {
                storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
            }

            Log.d("ConfirmLog", "transferMessage: cell = $cell")


            val content = BagOfCells(cell).toByteArray().toBase64()

            Log.d("ConfirmLog", "transferMessage: content = $content")
            val res = emulationApi.emulateMessageToWallet(EmulateMessageToEventRequest(
                boc = content

            ))

            Log.d("ConfirmLog", "transferMessage: res = $res")*/

        } catch (e: Throwable) {
            Log.e("ConfirmLog", "error", e)
        }
    }

    private fun buildRecipientItems(
        context: Context,
        recipient: SendScreenFeature.Recipient
    ): List<ConfirmScreenState.Item> {
        destinationAddress = recipient.address
        val items = arrayListOf<ConfirmScreenState.Item>()
        if (recipient.name != null) {
            items.add(ConfirmScreenState.Item(
                context.getString(R.string.recipient),
                recipient.name
            ))
            items.add(ConfirmScreenState.Item(
                context.getString(R.string.recipient_address),
                recipient.address.shortAddress
            ))
        } else {
            items.add(ConfirmScreenState.Item(
                context.getString(R.string.recipient),
                recipient.address.shortAddress
            ))
        }
        return items
    }

    private suspend fun buildAmountItems(
        context: Context,
        amount: SendScreenFeature.Amount
    ): List<ConfirmScreenState.Item> = withContext(Dispatchers.IO) {
        amountValue = amount.amount
        val items = arrayListOf<ConfirmScreenState.Item>()
        val wallet = App.walletManager.getWalletInfo() ?: return@withContext items


        val tonInCurrency = from(SupportedTokens.TON, wallet.accountId)
            .value(amount.amount)
            .to(currency)

        items.add(ConfirmScreenState.Item(
            context.getString(R.string.amount),
            Coin.format(value = amount.amount),
            Coin.format(currency, tonInCurrency)
        ))

        return@withContext items
    }

    private fun buildCommentItems(
        context: Context,
        recipient: SendScreenFeature.Recipient
    ): List<ConfirmScreenState.Item> {
        comment = recipient.comment
        val items = arrayListOf<ConfirmScreenState.Item>()
        if (recipient.comment.isNotEmpty()) {
            items.add(ConfirmScreenState.Item(
                context.getString(R.string.comment),
                recipient.comment
            ))
        }
        return items
    }

    fun updateItems(
        context: Context,
        recipient: SendScreenFeature.Recipient?,
        amount: SendScreenFeature.Amount?
    ) {
        viewModelScope.launch {
            val items = arrayListOf<ConfirmScreenState.Item>()
            recipient?.let {
                items.addAll(buildRecipientItems(context, it))
            }

            amount?.let {
                items.addAll(buildAmountItems(context, it))
            }

            recipient?.comment?.let {
                items.addAll(buildCommentItems(context, recipient))
            }

            updateUiState {
                it.copy(items = items)
            }
        }

    }
}