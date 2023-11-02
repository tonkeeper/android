package ton.console.model

import android.util.ArrayMap
import ton.SupportedTokens

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