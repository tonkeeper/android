package com.tonkeeper.fragment.send.confirm

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.transaction.TransactionHelper
import com.tonkeeper.fragment.send.SendScreenFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import uikit.mvi.UiFeature

class ConfirmScreenFeature: UiFeature<ConfirmScreenState, ConfirmScreenEffect>(ConfirmScreenState()) {

    private val currency: SupportedCurrency
        get() = App.settings.currency


    private var destinationAddress: String = ""
    private var amountValue = 0f
    private var comment: String? = null
    private var fee: Long = 0

    fun send() {
        viewModelScope.launch {
            transferMessage()
        }
    }

    private suspend fun transferMessage() = withContext(Dispatchers.IO) {
        Log.d("ConfirmLog", "start: $destinationAddress")
        try {
            val walletManager = App.walletManager
            val wallet = walletManager.getWalletInfo()!!

            TransactionHelper.send(wallet, destinationAddress, amountValue, comment, false)

            Log.d("ConfirmLog", "done!")

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
        fee = amount.fee

        val items = arrayListOf<ConfirmScreenState.Item>()
        val wallet = App.walletManager.getWalletInfo() ?: return@withContext items


        val tonInCurrency = from(SupportedTokens.TON, wallet.accountId)
            .value(amount.amount)
            .to(currency)

        items.add(ConfirmScreenState.Item(
            context.getString(R.string.amount),
            Coin.format(value = amount.amount),
            "≈ " + Coin.format(currency, tonInCurrency)
        ))

        if (fee > 0) {
            val feeInCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(fee)
                .to(currency)

            items.add(ConfirmScreenState.Item(
                context.getString(R.string.fee),
                "≈ " + Coin.format(value = fee, decimals = 9),
                "≈ " + Coin.format(currency, feeInCurrency, decimals = 9)
            ))
        }

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