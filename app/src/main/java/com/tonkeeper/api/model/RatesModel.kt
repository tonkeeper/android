package com.tonkeeper.api.model

import androidx.collection.ArrayMap
import com.tonkeeper.SupportedTokens

data class RatesModel(
    val tokens: ArrayMap<String, RatesTokenModel>
) {

    val ton: RatesTokenModel
        get() = get(SupportedTokens.TON)!!

    fun get(token: SupportedTokens): RatesTokenModel? {
        return get(token.code)
    }

    fun get(key: String): RatesTokenModel? {
        return tokens[key.uppercase()]
    }
}