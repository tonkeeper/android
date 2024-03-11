package com.tonapps.tonkeeper.ui.screen.init

import androidx.lifecycle.SavedStateHandle
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.wallet.api.entity.AccountPreviewEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

class InitModelState(private val savedStateHandle: SavedStateHandle) {

    companion object {
        private const val PASSCODE_KEY = "passcode"
        private const val LABEL_KEY = "label"
        private const val WATCH_ACCOUNT_KEY = "watch_account"
        private const val MNEMONIC_KEY = "mnemonic"
        private const val ACCOUNTS = "accounts"
        private const val SEED = "seed"
    }

    var passcode: String?
        get() = savedStateHandle[PASSCODE_KEY]
        set(value) = savedStateHandle.set(PASSCODE_KEY, value)

    var label: WalletLabel?
        get() = savedStateHandle[LABEL_KEY]
        set(value) = savedStateHandle.set(LABEL_KEY, value)

    var watchAccount: AccountPreviewEntity?
        get() = savedStateHandle[WATCH_ACCOUNT_KEY]
        set(value) = savedStateHandle.set(WATCH_ACCOUNT_KEY, value)

    var mnemonic: List<String>?
        get() = savedStateHandle[MNEMONIC_KEY]
        set(value) = savedStateHandle.set(MNEMONIC_KEY, value)

    var accounts: List<AccountItem>?
        get() = savedStateHandle[ACCOUNTS]
        set(value) = savedStateHandle.set(ACCOUNTS, value)

    var seed: ByteArray?
        get() = savedStateHandle[SEED]
        set(value) = savedStateHandle.set(SEED, value)

    val accountsFlow: Flow<List<AccountItem>?> = savedStateHandle.getStateFlow(ACCOUNTS, null)
}