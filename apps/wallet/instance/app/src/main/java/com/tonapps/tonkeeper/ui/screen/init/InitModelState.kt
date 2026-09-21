package com.tonapps.tonkeeper.ui.screen.init

import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import com.tonapps.blockchain.ton.MnemonicType
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.extensions.readBooleanCompat
import com.tonapps.extensions.writeBooleanCompat
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import kotlinx.coroutines.flow.filterNotNull
import org.ton.kotlin.crypto.PublicKeyEd25519

class InitModelState(private val savedStateHandle: SavedStateHandle) {

    data class PublicKey(
        val new: Boolean = false,
        val publicKey: PublicKeyEd25519
    ): Parcelable {

        constructor(parcel: Parcel) : this(
            parcel.readBooleanCompat(),
            parcel.readString()!!.publicKeyFromHex()
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

    val labelFlow = savedStateHandle.getStateFlow<Wallet.Label?>(LABEL_KEY, null).filterNotNull()

    var passcode: String?
        get() = savedStateHandle[PASSCODE_KEY]
        set(value) = savedStateHandle.set(PASSCODE_KEY, value)

    var wordsCount: Int
        get() = savedStateHandle[WORDS_COUNT_KEY] ?: 12
        set(value) = savedStateHandle.set(WORDS_COUNT_KEY, value)

    var label: Wallet.Label?
        get() = savedStateHandle[LABEL_KEY]
        set(value) = savedStateHandle.set(LABEL_KEY, value)

    val isEmptyName: Boolean
        get() = label == null || label?.name.isNullOrBlank()

    var watchAccount: AccountDetailsEntity?
        get() = savedStateHandle[WATCH_ACCOUNT_KEY]
        set(value) = savedStateHandle.set(WATCH_ACCOUNT_KEY, value)

    var mnemonic: Mnemonic?
        get() = savedStateHandle.get<ByteArray>(MNEMONIC_KEY)?.let { Mnemonic.fromBytes(it) }
        set(value) = savedStateHandle.set(MNEMONIC_KEY, value?.toBytes())

    val hasMnemonic: Boolean
        get() = savedStateHandle.get<ByteArray>(MNEMONIC_KEY) != null

    // User's explicit choice for a seed that is valid as both a TON and a BIP39 mnemonic. Null when the
    // seed is unambiguous (the type is then detected on the fly).
    var mnemonicType: MnemonicType?
        get() = savedStateHandle.get<String>(MNEMONIC_TYPE_KEY)?.let { MnemonicType.valueOf(it) }
        set(value) = savedStateHandle.set(MNEMONIC_TYPE_KEY, value?.name)

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

    var enableBiometry: Boolean
        get() = savedStateHandle[ENABLE_BIOMETRY_KEY] ?: false
        set(value) = savedStateHandle.set(ENABLE_BIOMETRY_KEY, value)

    var keystone: WalletEntity.Keystone?
        get() = savedStateHandle[KEYSTONE_KEY]
        set(value) = savedStateHandle.set(KEYSTONE_KEY, value)

    var backupDone: Boolean
        get() = savedStateHandle[BACKUP_DONE_KEY] ?: false
        set(value) = savedStateHandle.set(BACKUP_DONE_KEY, value)

    var walletsCount: Int
        get() = savedStateHandle[WALLETS_COUNT_KEY] ?: -1
        set(value) = savedStateHandle.set(WALLETS_COUNT_KEY, value)

    fun clearMnemonic() {
        savedStateHandle.get<ByteArray>(MNEMONIC_KEY)?.fill(0)
        savedStateHandle.set<ByteArray?>(MNEMONIC_KEY, null)
    }

    companion object {
        private const val PASSCODE_KEY = "passcode"
        private const val LABEL_KEY = "label"
        private const val WATCH_ACCOUNT_KEY = "watch_account"
        private const val MNEMONIC_KEY = "mnemonic"
        private const val MNEMONIC_TYPE_KEY = "mnemonic_type"
        private const val ACCOUNTS = "accounts"
        private const val PUBLIC_KEY = "public_key"
        private const val LEDGER_CONNECT_DATA = "ledger_connect_data"
        private const val ENABLE_PUSH_KEY = "enable_push"
        private const val ENABLE_BIOMETRY_KEY = "enable_biometry"
        private const val KEYSTONE_KEY = "keystone"
        private const val WORDS_COUNT_KEY = "words_count"
        private const val BACKUP_DONE_KEY = "backup_done"
        private const val WALLETS_COUNT_KEY = "wallets_count"
    }
}
