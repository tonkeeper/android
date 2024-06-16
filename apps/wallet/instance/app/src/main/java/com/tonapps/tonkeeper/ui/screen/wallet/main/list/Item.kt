package com.tonapps.tonkeeper.ui.screen.wallet.main.list

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.extensions.readArrayCompat
import com.tonapps.extensions.readBooleanCompat
import com.tonapps.extensions.readCharSequenceCompat
import com.tonapps.extensions.readEnum
import com.tonapps.extensions.readParcelableCompat
import com.tonapps.extensions.writeArrayCompat
import com.tonapps.extensions.writeBooleanCompat
import com.tonapps.extensions.writeCharSequenceCompat
import com.tonapps.extensions.writeEnum
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity

sealed class Item(type: Int): BaseListItem(type), Parcelable {

    companion object {
        const val TYPE_BALANCE = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_TOKEN = 2
        const val TYPE_SPACE = 3
        const val TYPE_SKELETON = 4
        const val TYPE_PUSH = 5
        const val TYPE_TITLE = 6
        const val TYPE_MANAGE = 7

        fun createFromParcel(parcel: Parcel): Item {
            return when (parcel.readInt()) {
                TYPE_BALANCE -> Balance(parcel)
                TYPE_ACTIONS -> Actions(parcel)
                TYPE_TOKEN -> Token(parcel)
                TYPE_SPACE -> Space(parcel)
                TYPE_SKELETON -> Skeleton(parcel)
                TYPE_PUSH -> Push(parcel)
                TYPE_MANAGE -> Manage(parcel)
                else -> throw IllegalArgumentException("Unknown type")
            }
        }
    }

    enum class Status {
        Default,
        Updating,
        NoInternet,
        SendingTransaction,
        TransactionConfirmed,
        Unknown,
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(type)
        marshall(dest, flags)
    }

    abstract fun marshall(dest: Parcel, flags: Int)

    override fun describeContents(): Int {
        return 0
    }

    data class Balance(
        val balance: CharSequence,
        val address: String,
        val walletType: Wallet.Type,
        val status: Status,
        val hiddenBalance: Boolean
    ): Item(TYPE_BALANCE) {

        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readEnum(Wallet.Type::class.java)!!,
            parcel.readEnum(Status::class.java)!!,
            parcel.readBooleanCompat()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeString(balance.toString())
            dest.writeString(address)
            dest.writeEnum(walletType)
            dest.writeEnum(status)
            dest.writeBooleanCompat(hiddenBalance)
        }

        companion object CREATOR : Parcelable.Creator<Balance> {
            override fun createFromParcel(parcel: Parcel) = Balance(parcel)

            override fun newArray(size: Int): Array<Balance?> = arrayOfNulls(size)
        }
    }

    data class Actions(
        val address: String,
        val token: TokenEntity,
        val walletType: Wallet.Type,
        val swapUri: Uri,
        val disableSwap: Boolean
    ): Item(TYPE_ACTIONS) {

        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readParcelableCompat()!!,
            parcel.readEnum(Wallet.Type::class.java)!!,
            parcel.readParcelableCompat()!!,
            parcel.readBooleanCompat()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeString(address)
            dest.writeParcelable(token, flags)
            dest.writeEnum(walletType)
            dest.writeParcelable(swapUri, flags)
            dest.writeBooleanCompat(disableSwap)
        }

        companion object CREATOR : Parcelable.Creator<Actions> {
            override fun createFromParcel(parcel: Parcel) = Actions(parcel)

            override fun newArray(size: Int): Array<Actions?> = arrayOfNulls(size)
        }
    }

    data class Token(
        val position: ListCell.Position,
        val iconUri: Uri,
        val address: String,
        val symbol: String,
        val name: String,
        val balance: Coins,
        val balanceFormat: CharSequence,
        val fiat: Coins,
        val fiatFormat: CharSequence,
        val rate: CharSequence,
        val rateDiff24h: String,
        val verified: Boolean,
        val testnet: Boolean,
        val hiddenBalance: Boolean
    ): Item(TYPE_TOKEN) {

        val isTON = symbol == TokenEntity.TON.symbol

        val isUSDT = symbol == TokenEntity.USDT.symbol

        constructor(parcel: Parcel) : this(
            parcel.readEnum(ListCell.Position::class.java)!!,
            parcel.readParcelableCompat()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readParcelableCompat()!!,
            parcel.readCharSequenceCompat() ?: "",
            parcel.readParcelableCompat()!!,
            parcel.readCharSequenceCompat()!!,
            parcel.readCharSequenceCompat()!!,
            parcel.readString()!!,
            parcel.readBooleanCompat(),
            parcel.readBooleanCompat(),
            parcel.readBooleanCompat()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeEnum(position)
            dest.writeParcelable(iconUri, flags)
            dest.writeString(address)
            dest.writeString(symbol)
            dest.writeString(name)
            dest.writeParcelable(balance, flags)
            dest.writeCharSequenceCompat(balanceFormat)
            dest.writeParcelable(fiat, flags)
            dest.writeCharSequenceCompat(fiatFormat)
            dest.writeCharSequenceCompat(rate)
            dest.writeString(rateDiff24h)
            dest.writeBooleanCompat(verified)
            dest.writeBooleanCompat(testnet)
            dest.writeBooleanCompat(hiddenBalance)
        }

        companion object CREATOR : Parcelable.Creator<Token> {
            override fun createFromParcel(parcel: Parcel) = Token(parcel)

            override fun newArray(size: Int): Array<Token?> = arrayOfNulls(size)
        }
    }

    data class Space(val value: Boolean = true): Item(TYPE_SPACE) {

        constructor(parcel: Parcel) : this(
            parcel.readBooleanCompat()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeBooleanCompat(value)
        }

        companion object CREATOR : Parcelable.Creator<Space> {
            override fun createFromParcel(parcel: Parcel) = Space(parcel)

            override fun newArray(size: Int): Array<Space?> = arrayOfNulls(size)
        }
    }

    data class Skeleton(val value: Boolean = true): Item(TYPE_SKELETON) {

        constructor(parcel: Parcel) : this(
            parcel.readBooleanCompat()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeBooleanCompat(value)
        }

        companion object CREATOR : Parcelable.Creator<Skeleton> {
            override fun createFromParcel(parcel: Parcel) = Skeleton(parcel)

            override fun newArray(size: Int): Array<Skeleton?> = arrayOfNulls(size)
        }
    }

    data class Push(
        val events: List<AppPushEntity>,
        val apps: List<DAppEntity>
    ): Item(TYPE_PUSH) {

        constructor(parcel: Parcel) : this(
            parcel.readArrayCompat(AppPushEntity::class.java)?.toList()!!,
            parcel.readArrayCompat(DAppEntity::class.java)?.toList()!!,
        )

        val text = events.first().message

        val iconUris: List<Uri> by lazy {
            apps.map { Uri.parse(it.manifest.iconUrl) }
        }

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeArrayCompat(events.toTypedArray())
            dest.writeArrayCompat(apps.toTypedArray())
        }

        companion object CREATOR : Parcelable.Creator<Push> {
            override fun createFromParcel(parcel: Parcel) = Push(parcel)

            override fun newArray(size: Int): Array<Push?> = arrayOfNulls(size)
        }
    }

    data class Title(
        val title: CharSequence
    ): Item(TYPE_TITLE) {

        constructor(context: Context, resId: Int) : this(
            context.getText(resId)
        )

        constructor(parcel: Parcel) : this(
            parcel.readCharSequenceCompat()!!
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeCharSequenceCompat(title)
        }

        companion object CREATOR : Parcelable.Creator<Title> {
            override fun createFromParcel(parcel: Parcel) = Title(parcel)

            override fun newArray(size: Int): Array<Title?> = arrayOfNulls(size)
        }
    }

    data class Manage(val value: Boolean): Item(TYPE_MANAGE) {

        constructor(parcel: Parcel) : this(
            parcel.readBooleanCompat()
        )

        override fun marshall(dest: Parcel, flags: Int) {
            dest.writeBooleanCompat(value)
        }

        companion object CREATOR : Parcelable.Creator<Manage> {
            override fun createFromParcel(parcel: Parcel) = Manage(parcel)

            override fun newArray(size: Int): Array<Manage?> = arrayOfNulls(size)
        }
    }
}