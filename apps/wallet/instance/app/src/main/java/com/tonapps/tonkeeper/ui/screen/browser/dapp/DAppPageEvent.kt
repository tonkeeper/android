package com.tonapps.tonkeeper.ui.screen.browser.dapp

import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import org.json.JSONObject

/**
 * An event a dApp page sends through its bridge, as it goes out: its primitive params with keys
 * Aptabase accepts, hex runs masked, and the marks that set it apart from the wallet's own events.
 */
internal object DAppPageEvent {

    private const val IS_EXTERNAL_WEB_VIEW = "isExternalWebView"
    private const val EVENT_PROVIDER = "eventProvider"
    private const val MAX_KEY_LENGTH = 40
    private const val MAX_VALUE_LENGTH = 1024
    private const val HEX_MASK = "<hex>"
    private const val TOO_LONG_MASK = "<too_long>"

    private val tonAddressCandidate = Regex("[EUk0][Qf][A-Za-z0-9_+/-]{46}")

    private val addressOrHash = Regex("(0x)?[0-9a-fA-F]{40,}|(?=[A-Za-z_-]*[0-9])[A-Za-z0-9_-]{32,}")

    // An Asset ID names a public contract, so its address stays.
    private val assetId = Regex("[a-z0-9]+/(mainnet|testnet)/[a-z0-9]+(/[^/\\s]+)?")

    fun params(raw: Map<String, Any?>, provider: String): MutableMap<String, Any> {
        val params = LinkedHashMap<String, Any>()
        for ((key, value) in raw) {
            if (key.length > MAX_KEY_LENGTH) {
                continue
            }
            when (value) {
                is String -> params[key] = if (value.length > MAX_VALUE_LENGTH) {
                    TOO_LONG_MASK
                } else {
                    mask(value)
                }
                is Number, is Boolean -> params[key] = value
                else -> Unit
            }
        }
        params[IS_EXTERNAL_WEB_VIEW] = 1
        params[EVENT_PROVIDER] = provider
        return params
    }

    fun rawParams(json: JSONObject?): Map<String, Any?> {
        json ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.opt(it) }
    }

    private fun mask(value: String): String {
        if (assetId.matches(value)) {
            return value
        }
        return addressOrHash.replace(maskTonAddresses(value), HEX_MASK)
    }

    private fun maskTonAddresses(value: String): String {
        val masked = StringBuilder()
        var copiedUntil = 0
        var searchFrom = 0
        while (true) {
            val candidate = tonAddressCandidate.find(value, searchFrom) ?: break
            if (candidate.value.replace('+', '-').replace('/', '_').isValidTonAddress()) {
                masked.append(value, copiedUntil, candidate.range.first).append(HEX_MASK)
                copiedUntil = candidate.range.last + 1
                searchFrom = copiedUntil
            } else {
                searchFrom = candidate.range.first + 1
            }
        }
        return masked.append(value, copiedUntil, value.length).toString()
    }
}