package com.tonapps.tonkeeper.ui.screen.token.picker

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.api.entity.TokenEntity
import uikit.base.BaseArgs

data class TokenPickerArgs(
    val requestKey: String,
    val selectedToken: TokenEntity,
    val allowedTokens: List<String>,
): BaseArgs() {

    private companion object {
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_SELECTED_TOKEN = "selected_token"
        private const val ARG_ALLOWED_TOKENS = "allowed_tokens"
    }

    constructor(bundle: Bundle) : this(
        requestKey = bundle.getString(ARG_REQUEST_KEY)!!,
        selectedToken = bundle.getParcelableCompat<TokenEntity>(ARG_SELECTED_TOKEN)!!,
        allowedTokens = bundle.getStringArrayList(ARG_ALLOWED_TOKENS)!!
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_REQUEST_KEY, requestKey)
        putParcelable(ARG_SELECTED_TOKEN, selectedToken)
        putStringArrayList(ARG_ALLOWED_TOKENS, ArrayList(allowedTokens))
    }
}