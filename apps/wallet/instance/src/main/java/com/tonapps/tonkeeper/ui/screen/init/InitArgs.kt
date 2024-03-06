package com.tonapps.tonkeeper.ui.screen.init

import androidx.lifecycle.SavedStateHandle
import com.tonapps.wallet.data.account.WalletColor

class InitArgs(
    private val savedStateHandle: SavedStateHandle
) {

    private companion object {
        private const val WORDS_KEY = "words"
        private const val NAME_KEY = "name"
        private const val COLOR_KEY = "color"
        private const val EMOJI_KEY = "emoji"
        private const val PASSCODE_KEY = "passcode"
        private const val WATCH_ACCOUNT_ID_KEY = "watch_account_id"
        private const val PK_BASE64_KEY = "pk_base64"
    }

    var words: List<String>?
        get() {
            val value = savedStateHandle.get<List<String>>(WORDS_KEY) ?: return null
            if (value.isEmpty()) {
                return null
            }
            return value
        }
        set(value) = savedStateHandle.set(WORDS_KEY, value)

    var name: String?
        get() = savedStateHandle[NAME_KEY]
        set(value) = savedStateHandle.set(NAME_KEY, value)

    var color: Int
        get() = savedStateHandle[COLOR_KEY] ?: WalletColor.all.first()
        set(value) = savedStateHandle.set(COLOR_KEY, value)

    var emoji: CharSequence
        get() = savedStateHandle[EMOJI_KEY] ?: "\uD83D\uDC8E"
        set(value) = savedStateHandle.set(EMOJI_KEY, value)

    var passcode: String?
        get() = savedStateHandle[PASSCODE_KEY]
        set(value) = savedStateHandle.set(PASSCODE_KEY, value)

    var watchAccountId: String?
        get() = savedStateHandle[WATCH_ACCOUNT_ID_KEY]
        set(value) = savedStateHandle.set(WATCH_ACCOUNT_ID_KEY, value)

    var pkBase64: String?
        get() = savedStateHandle[PK_BASE64_KEY]
        set(value) = savedStateHandle.set(PK_BASE64_KEY, value)

}