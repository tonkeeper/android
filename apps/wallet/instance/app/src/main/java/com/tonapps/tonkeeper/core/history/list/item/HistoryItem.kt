package com.tonapps.tonkeeper.core.history.list.item

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.locale
import com.tonapps.extensions.readBooleanCompat
import com.tonapps.extensions.readCharSequenceCompat
import com.tonapps.extensions.readEnum
import com.tonapps.extensions.readParcelableCompat
import com.tonapps.extensions.writeBooleanCompat
import com.tonapps.extensions.writeCharSequenceCompat
import com.tonapps.extensions.writeEnum
import com.tonapps.tonkeeper.core.history.ActionOutStatus
import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.events.recipient
import com.tonapps.wallet.data.events.sender
import com.tonapps.wallet.data.settings.SpamTransactionState
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.AccountAddress
import io.tonapi.models.EncryptedComment
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

sealed class HistoryItem(
    type: Int,
): BaseListItem(type), Parcelable {

    companion object {
        const val TYPE_ACTION = 1
        const val TYPE_HEADER = 2
        const val TYPE_LOADER = 3
        const val TYPE_APP = 4
        const val TYPE_EMPTY = 5
        const val TYPE_FAILED = 6

        fun createFromParcel(parcel: Parcel): HistoryItem {
            return when (parcel.readInt()) {
                TYPE_ACTION -> Event(parcel)
                TYPE_HEADER -> Header(parcel)
                TYPE_LOADER -> Loader(parcel)
                TYPE_APP -> App(parcel)
                TYPE_EMPTY -> Empty(parcel)
                TYPE_FAILED -> Failed(parcel)
                else -> throw IllegalArgumentException("Unknown type")
            }
        }
    }

    val timestampForSort: Long by lazy {
        when (this) {
            is Event -> this.timestamp - this.index
            is Loader -> this.date
            is App -> this.timestamp
            is Header -> this.date
            is Failed -> this.date
            else -> 0L
        }
    }

    val uniqueId: String by lazy {
        when (this) {
            is Event -> "event_${this.txId}_${this.index}"
            is Loader -> "loader_${this.index}"
            is App -> "app_${this.timestamp}"
            is Header -> "header_${this.title}"
            is Failed -> "failed_${this.date}"
            is Empty -> "empty_${this.title}"
            else -> "unknown"
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(type)
        marshall(dest, flags)
    }

    abstract fun marshall(dest: Parcel, flags: Int)

    override fun describeContents(): Int {
        return 0
    }

    abstract class Service(type: Int): HistoryItem(type)

    data class Loader(
        val index: Int,
        val date: Long
    ): Service(TYPE_LOADER) {

        constructor(parcel: Parcel) : this(
            index = parcel.readInt(),
            date = parcel.readLong()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeInt(index)
            dest.writeLong(date)
        }

        companion object CREATOR : Parcelable.Creator<Loader> {
            override fun createFromParcel(parcel: Parcel) = Loader(parcel)

            override fun newArray(size: Int): Array<Loader?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class Header(
        val title: String,
        val date: Long,
    ): HistoryItem(TYPE_HEADER) {

        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readLong()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeString(title)
            dest.writeLong(date)
        }

        companion object CREATOR : Parcelable.Creator<Header> {
            override fun createFromParcel(parcel: Parcel) = Header(parcel)

            override fun newArray(size: Int): Array<Header?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class Failed(
        val text: String = "",
        val date: Long = System.currentTimeMillis()
    ): Service(TYPE_FAILED) {

        constructor(context: Context, resId: Int = Localization.unknown_error) : this(context.getString(resId))

        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readLong()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeString(text)
            dest.writeLong(date)
        }

        companion object CREATOR : Parcelable.Creator<Failed> {
            override fun createFromParcel(parcel: Parcel) = Failed(parcel)

            override fun newArray(size: Int): Array<Failed?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class Empty(
        val title: String
    ): Service(TYPE_EMPTY) {

        constructor(context: Context, resId: Int) : this(context.getString(resId))

        constructor(parcel: Parcel) : this(
            parcel.readString()!!
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeString(title)
        }

        companion object CREATOR : Parcelable.Creator<Empty> {
            override fun createFromParcel(parcel: Parcel) = Empty(parcel)

            override fun newArray(size: Int): Array<Empty?> {
                return arrayOfNulls(size)
            }
        }

    }

    data class App(
        val iconUri: Uri,
        val title: String,
        val body: String,
        val date: String,
        val url: Uri,
        val timestamp: Long,
        val deepLink: String,
        val wallet: WalletEntity,
    ): HistoryItem(TYPE_APP) {

        val isClickable: Boolean
            get() = deepLink.isNotBlank()

        constructor(context: Context, wallet: WalletEntity, push: AppPushEntity) : this(
            iconUri = push.iconUrl.toUri(),
            title = push.title,
            body = push.message,
            date = DateHelper.formattedDate(push.timestamp, context.locale),
            url = push.url,
            timestamp = push.timestamp,
            deepLink = push.deeplink ?: push.url.toString(),
            wallet = wallet
        )

        constructor(parcel: Parcel) : this(
            iconUri = parcel.readParcelableCompat()!!,
            title = parcel.readString()!!,
            body = parcel.readString()!!,
            date = parcel.readString()!!,
            url = parcel.readParcelableCompat()!!,
            timestamp = parcel.readLong(),
            deepLink = parcel.readString()!!,
            wallet = parcel.readParcelableCompat()!!
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeParcelable(iconUri, flags)
            dest.writeString(title)
            dest.writeString(body)
            dest.writeString(date)
            dest.writeParcelable(url, flags)
            dest.writeLong(timestamp)
            dest.writeString(deepLink)
            dest.writeParcelable(wallet, flags)
        }

        companion object CREATOR : Parcelable.Creator<App> {
            override fun createFromParcel(parcel: Parcel) = App(parcel)

            override fun newArray(size: Int): Array<App?> {
                return arrayOfNulls(size)
            }
        }
    }

    @Parcelize
    data class Account(
        val address: String,
        val name: String?,
        val isWallet: Boolean,
        val icon: String?,
        val isScam: Boolean,
    ): Parcelable {

        constructor(account: AccountAddress, testnet: Boolean) : this(
            address = account.address.toUserFriendly(
                wallet = account.isWallet,
                testnet = testnet,
            ),
            name = account.name,
            isWallet = account.isWallet,
            icon = account.icon,
            isScam = account.isScam
        )

        companion object {

            fun ofSender(action: io.tonapi.models.Action, testnet: Boolean): Account? {
                return action.sender?.let { Account(it, testnet) }
            }

            fun ofRecipient(action: io.tonapi.models.Action, testnet: Boolean): Account? {
                return action.recipient?.let { Account(it, testnet) }
            }
        }
    }

    data class Event(
        val index: Int,
        val blockchain: Blockchain = Blockchain.TON,
        val txId: String,
        val iconURL: String? = null,
        val action: ActionType,
        val title: String,
        val subtitle: String,
        val timestamp: Long = 0L,
        val comment: Comment? = null,
        val value: CharSequence,
        val value2: CharSequence = "",
        val currency: CharSequence? = null,
        val nft: NftEntity? = null,
        val tokenAddress: String? = null,
        val tokenCode: String? = null,
        val date: String = "",
        val dateDetails: String = "",
        val pending: Boolean = false,
        val position: ListCell.Position = ListCell.Position.SINGLE,
        val coinIconUrl: String = "",
        val coinIconUrl2: String = "",
        val fee: CharSequence? = null,
        val feeInCurrency: CharSequence? = null,
        val isOut: Boolean,
        val sender: Account?,
        val recipient: Account?,
        val lt: Long = 0L,
        val failed: Boolean,
        val hiddenBalance: Boolean = false,
        val unverifiedToken: Boolean = false,
        val isScam: Boolean,
        val refund: CharSequence? = null,
        val refundInCurrency: CharSequence? = null,
        val wallet: WalletEntity,
        val isMaybeSpam: Boolean = false,
        val spamState: SpamTransactionState = SpamTransactionState.UNKNOWN,
        val actionOutStatus: ActionOutStatus,
        val showNetwork: Boolean = false,
    ): HistoryItem(TYPE_ACTION) {

        val account: Account?
            get() = if (isOut) recipient else sender

        val isStake: Boolean
            get() = action == ActionType.WithdrawStake || action == ActionType.WithdrawStakeRequest || action == ActionType.DepositStake

        @Parcelize
        data class Comment(
            val type: Type,
            val body: String,
        ): Parcelable {

            enum class Type {
                Text, Simple, OriginalEncrypted
            }

            companion object {

                fun create(
                    text: String?,
                    encrypted: EncryptedComment?,
                    localText: String?
                ): Comment? {
                    if (!text.isNullOrBlank()) {
                        return Comment(Type.Text, text)
                    }
                    if (!localText.isNullOrBlank()) {
                        return Comment(Type.OriginalEncrypted, localText)
                    }
                    val data = encrypted ?: return null
                    if (data.encryptionType == "simple" && data.cipherText.isNotBlank()) {
                        return Comment(Type.Simple, data.cipherText)
                    }
                    return null
                }
            }

            val isEncrypted: Boolean
                get() = type != Type.Text && type != Type.OriginalEncrypted
        }

        @IgnoredOnParcel
        val isSwap: Boolean
            get() = action == ActionType.Swap

        @IgnoredOnParcel
        val hasNft: Boolean
            get() = nft != null

        @IgnoredOnParcel
        val isTon: Boolean
            get() = tokenCode == "TON"

        constructor(parcel: Parcel) : this(
            index = parcel.readInt(),
            txId = parcel.readString()!!,
            iconURL = parcel.readString(),
            action = parcel.readEnum(ActionType::class.java)!!,
            title = parcel.readString()!!,
            subtitle = parcel.readString()!!,
            timestamp = parcel.readLong(),
            comment = parcel.readParcelableCompat(),
            value = parcel.readCharSequenceCompat()!!,
            value2 = parcel.readCharSequenceCompat()!!,
            currency = parcel.readCharSequenceCompat(),
            nft = parcel.readParcelableCompat(),
            tokenCode = parcel.readString(),
            date = parcel.readString()!!,
            pending = parcel.readBooleanCompat(),
            position = parcel.readEnum(ListCell.Position::class.java)!!,
            coinIconUrl = parcel.readString()!!,
            coinIconUrl2 = parcel.readString()!!,
            fee = parcel.readCharSequenceCompat(),
            feeInCurrency = parcel.readCharSequenceCompat(),
            isOut = parcel.readBooleanCompat(),
            sender = parcel.readParcelableCompat(),
            recipient = parcel.readParcelableCompat(),
            lt = parcel.readLong(),
            failed = parcel.readBooleanCompat(),
            hiddenBalance = parcel.readBooleanCompat(),
            unverifiedToken = parcel.readBooleanCompat(),
            isScam = parcel.readBooleanCompat(),
            refund = parcel.readCharSequenceCompat(),
            refundInCurrency = parcel.readCharSequenceCompat(),
            wallet = parcel.readParcelableCompat()!!,
            isMaybeSpam = parcel.readBooleanCompat(),
            spamState = parcel.readEnum(SpamTransactionState::class.java)!!,
            actionOutStatus = parcel.readEnum(ActionOutStatus::class.java)!!
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeInt(index)
            dest.writeString(txId)
            dest.writeString(iconURL)
            dest.writeEnum(action)
            dest.writeString(title)
            dest.writeString(subtitle)
            dest.writeLong(timestamp)
            dest.writeParcelable(comment, flags)
            dest.writeCharSequenceCompat(value)
            dest.writeCharSequenceCompat(value2)
            dest.writeCharSequenceCompat(currency)
            dest.writeParcelable(nft, flags)
            dest.writeString(tokenCode)
            dest.writeString(date)
            dest.writeBooleanCompat(pending)
            dest.writeEnum(position)
            dest.writeString(coinIconUrl)
            dest.writeString(coinIconUrl2)
            dest.writeCharSequenceCompat(fee)
            dest.writeCharSequenceCompat(feeInCurrency)
            dest.writeBooleanCompat(isOut)
            dest.writeParcelable(sender, flags)
            dest.writeParcelable(recipient, flags)
            dest.writeLong(lt)
            dest.writeBooleanCompat(failed)
            dest.writeBooleanCompat(hiddenBalance)
            dest.writeBooleanCompat(unverifiedToken)
            dest.writeBooleanCompat(isScam)
            dest.writeCharSequenceCompat(refund)
            dest.writeCharSequenceCompat(refundInCurrency)
            dest.writeParcelable(wallet, flags)
            dest.writeBooleanCompat(isMaybeSpam)
            dest.writeEnum(spamState)
            dest.writeEnum(actionOutStatus)
        }

        companion object CREATOR : Parcelable.Creator<Event> {
            override fun createFromParcel(parcel: Parcel) = Event(parcel)

            override fun newArray(size: Int): Array<Event?> {
                return arrayOfNulls(size)
            }
        }
    }
}