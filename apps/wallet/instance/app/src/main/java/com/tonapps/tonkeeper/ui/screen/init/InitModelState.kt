package com.tonapps.tonkeeper.ui.screen.init

import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.publicKey
import com.tonapps.blockchain.ton.extensions.safePublicKey
import com.tonapps.extensions.readBooleanCompat
import com.tonapps.extensions.writeBooleanCompat
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.security.hex
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.ton.api.pub.PublicKeyEd25519

class InitModelState(private val savedStateHandle: SavedStateHandle) {

    data class PublicKey(
        val new: Boolean = false,
        val publicKey: PublicKeyEd25519
    ): Parcelable {

        constructor(parcel: Parcel) : this(
            parcel.readBooleanCompat(),
            parcel.readString()!!.publicKey()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeBooleanCompat(new)
            parcel.writeString(publicKey.hex())
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<PublicKey> {
            override fun createFromParcel(parcel: Parcel) = PublicKey(parcel)
            override fun newArray(size: Int): Array<PublicKey?> = arrayOfNulls(size)
        }
    }

    companion object {
        private const val PASSCODE_KEY = "passcode"
        private const val LABEL_KEY = "label"
        private const val WATCH_ACCOUNT_KEY = "watch_account"
        private const val MNEMONIC_KEY = "mnemonic"
        private const val ACCOUNTS = "accounts"
        private const val PUBLIC_KEY = "public_key"
        private const val LEDGER_CONNECT_DATA = "ledger_connect_data"
        private const val ENABLE_PUSH_KEY = "enable_push"
        private const val KEYSTONE_KEY = "keystone"
    }

    val labelFlow = savedStateHandle.getStateFlow(LABEL_KEY, Wallet.Label("", "", Color.TRANSPARENT))

    var passcode: String?
        get() = savedStateHandle[PASSCODE_KEY]
        set(value) = savedStateHandle.set(PASSCODE_KEY, value)

    var label: Wallet.Label?
        get() = savedStateHandle[LABEL_KEY]
        set(value) = savedStateHandle.set(LABEL_KEY, value)

    val isEmptyLabel: Boolean
        get() = label == null || label?.isEmpty == true

    var watchAccount: AccountDetailsEntity?
        get() = savedStateHandle[WATCH_ACCOUNT_KEY]
        set(value) = savedStateHandle.set(WATCH_ACCOUNT_KEY, value)

    var mnemonic: List<String>?
        get() = savedStateHandle[MNEMONIC_KEY]
        set(value) = savedStateHandle.set(MNEMONIC_KEY, value)

    var accounts: List<AccountItem>?
        get() = savedStateHandle[ACCOUNTS]
        set(value) = savedStateHandle.set(ACCOUNTS, value)

    var publicKey: PublicKey?
        get() {
            return savedStateHandle.get<PublicKey>(PUBLIC_KEY)
        }
        set(value) = savedStateHandle.set(PUBLIC_KEY, value)

    var ledgerConnectData: LedgerConnectData?
        get() = savedStateHandle[LEDGER_CONNECT_DATA]
        set(value) = savedStateHandle.set(LEDGER_CONNECT_DATA, value)

    var enablePush: Boolean
        get() = savedStateHandle[ENABLE_PUSH_KEY] ?: false
        set(value) = savedStateHandle.set(ENABLE_PUSH_KEY, value)

    var keystone: WalletEntity.Keystone?
        get() = savedStateHandle[KEYSTONE_KEY]
        set(value) = savedStateHandle.set(KEYSTONE_KEY, value)

}