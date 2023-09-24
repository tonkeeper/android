package com.tonkeeper.ton.console.model

import android.util.ArrayMap
import com.tonkeeper.ton.SupportedTokens

data class RatesModel(
    val tokens: ArrayMap<String, RatesTokenModel>
) {

    fun get(token: SupportedTokens): RatesTokenModel? {
        return get(token.code)
    }

    fun get(key: String): RatesTokenModel? {
        return tokens[key.uppercase()]
    }
}