package com.tonapps.wallet.data.events.tx.model

import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.short4
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.ActionTypeOut
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class TxActionBody(
    val type: ActionType,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val value: String?,
    val amount: Amount,
    val product: Product?,
    val sender: Account?,
    val recipient: Account?,
    val flags: List<TxFlag>,
    val text: Text?
): Parcelable {

    val hasUnverifiedNft: Boolean
        get() = flags.contains(TxFlag.UnverifiedNft)

    val hasVerifiedNft: Boolean
        get() = flags.contains(TxFlag.VerifiedNft)

    val hasUnverifiedToken: Boolean
        get() = flags.contains(TxFlag.UnverifiedToken)

    val hasEncryptedText: Boolean
        get() = text is Text.Encrypted

    val isOut: Boolean
        get() = ActionTypeOut.contains(type)

    val currencies: List<WalletCurrency>
        get() = amount.currencies

    @Parcelize
    data class Account(
        val address: String,
        val isScam: Boolean = false,
        val isWallet: Boolean = true,
        val name: String? = null,
        val icon: String? = null,
        val testnet: Boolean
    ): Parcelable {

        @IgnoredOnParcel
        val userFriendlyAddress: String by lazy {
            address.toUserFriendly(
                wallet = isWallet,
                testnet = testnet
            )
        }

        @IgnoredOnParcel
        val title: String by lazy {
            name ?: userFriendlyAddress.short4
        }
    }

    @Parcelize
    sealed class Text: Parcelable {
        data class Plain(val text: String) : Text()
        data class Encrypted(val type: String, val cipher: String) : Text()
    }

    @Parcelize
    data class Product(
        val id: String,
        val type: Type,
        val title: String,
        val subtitle: String,
        val imageUrl: String,
    ): Parcelable {

        enum class Type {
            Nft
        }
    }

    @Parcelize
    data class Value(
        val value: Coins,
        val currency: WalletCurrency,
    ): Parcelable {

        @IgnoredOnParcel
        val formatted: CharSequence? by lazy {
            CurrencyFormatter.format(
                currency = currency.symbol,
                value = value,
                cutLongSymbol = true,
            )
        }

        @IgnoredOnParcel
        val formattedFull: CharSequence? by lazy {
            CurrencyFormatter.formatFull(currency.symbol, value, currency.decimals)
        }
    }

    @Parcelize
    data class Amount(
        val incoming: Value? = null,
        val outgoing: Value? = null
    ): Parcelable {

        val isEmpty: Boolean
            get() = incoming == null && outgoing == null

        val currencies: List<WalletCurrency>
            get() = listOfNotNull(incoming?.currency, outgoing?.currency)

        val incomingFormatted: CharSequence?
            get() = incoming?.formatted

        val outgoingFormatted: CharSequence?
            get() = outgoing?.formatted

    }

    class Builder(private val type: ActionType) {

        private var title: String = ""
        private var subtitle: String = ""
        private var imageUrl: String? = null
        private var amount: Amount = Amount()
        private var product: Product? = null
        private var sender: Account? = null
        private var recipient: Account? = null
        private val flags: MutableList<TxFlag> = mutableListOf()
        private var text: Text? = null
        private var value: String? = null

        fun setTitle(title: String) = apply { this.title = title }

        fun setSubtitle(subtitle: String) = apply { this.subtitle = subtitle }

        fun setSender(sender: Account) = apply { this.sender = sender }

        fun setImageUrl(imageUrl: String?) = apply { this.imageUrl = imageUrl }

        fun setRecipient(recipient: Account) = apply { this.recipient = recipient }

        fun setIncomingAmount(value: Coins, currency: WalletCurrency) = apply {
            this.amount = this.amount.copy(incoming = Value(value, currency))
        }

        fun setIncomingAmount(value: Value) = apply {
            this.amount = this.amount.copy(incoming = value)
        }

        fun setOutgoingAmount(value: Coins, currency: WalletCurrency = WalletCurrency.TON) = apply {
            this.amount = this.amount.copy(outgoing = Value(value, currency))
        }

        fun setOutgoingAmount(value: Value) = apply {
            this.amount = this.amount.copy(outgoing = value)
        }

        fun setTextComment(text: String) = apply {
            this.text = Text.Plain(text)
        }

        fun setTextEncrypted(type: String, cipher: String) = apply {
            this.text = Text.Encrypted(type, cipher)
        }

        fun setText(text: Text?) = apply {
            this.text = text
        }

        fun setProduct(product: Product?) = apply {
            this.product = product
        }

        fun addFlag(flag: TxFlag) = apply { flags.add(flag) }

        fun setValue(value: String?) = apply { this.value = value }

        fun build() = TxActionBody(
            type = type,
            title = title,
            subtitle = subtitle,
            imageUrl = imageUrl,
            amount = amount,
            product = product,
            sender = sender,
            recipient = recipient,
            flags = flags.toList(),
            text = text,
            value = value
        )

    }
}

