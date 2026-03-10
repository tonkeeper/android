package com.tonapps.wallet.data.events.tx.model

import android.os.Parcelable
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.events.ActionType
import kotlinx.parcelize.Parcelize

@Parcelize
data class TxAction(
    val body: TxActionBody,
    val status: Status = Status.Ok,
    val isMaybeSpam: Boolean,
): Parcelable {

    enum class Status {
        Ok, Failed, Unknown
    }

    val amount: TxActionBody.Amount
        get() = body.amount

    val type: ActionType
        get() = body.type

    val isFailed: Boolean
        get() = status == Status.Failed

    val title: String?
        get() = body.title.ifBlank { null }

    val recipient: TxActionBody.Account?
        get() = body.recipient

    val sender: TxActionBody.Account?
        get() = body.sender

    val account: TxActionBody.Account?
        get() = if (isOut) recipient else sender

    val subtitle: String?
        get() = body.subtitle.ifBlank {
            account?.title
        }

    val incomingFormatted: CharSequence?
        get() = body.amount.incomingFormatted

    val outgoingFormatted: CharSequence?
        get() = body.amount.outgoingFormatted

    val text: TxActionBody.Text?
        get() = body.text

    val product: TxActionBody.Product?
        get() = body.product

    val hasUnverifiedNft: Boolean
        get() = body.hasUnverifiedNft

    val hasVerifiedNft: Boolean
        get() = body.hasVerifiedNft

    val hasEncryptedText: Boolean
        get() = body.hasEncryptedText

    val hasUnverifiedToken: Boolean
        get() = body.hasUnverifiedToken

    val imageUrl: String?
        get() = body.imageUrl

    val isOut: Boolean
        get() = body.isOut

    val currencies: List<WalletCurrency>
        get() = body.currencies

    val tokens: List<WalletCurrency>
        get() = currencies.filter { !it.fiat }

    val value: TxActionBody.Value?
        get() = if (isOut) body.amount.outgoing else body.amount.incoming

    val primaryValue: TxActionBody.Value?
        get() = if (type == ActionType.Swap) {
            body.amount.outgoing ?: body.amount.incoming
        } else {
            body.amount.incoming ?: body.amount.outgoing
        }

    val encryptedText: TxActionBody.Text.Encrypted?
        get() = body.text as? TxActionBody.Text.Encrypted

}
