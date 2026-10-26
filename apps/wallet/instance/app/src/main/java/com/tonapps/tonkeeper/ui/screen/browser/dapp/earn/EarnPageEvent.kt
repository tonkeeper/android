package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

/**
 * An event the Earn page sends through `analytics.track`, as it goes out: its primitive params
 * with keys Aptabase accepts, hex runs masked, and the marks that set it apart from the wallet's
 * own Earn events.
 */
internal object EarnPageEvent {

    const val PROVIDER = "vaults.fyi"

    private const val IS_EXTERNAL_WEB_VIEW = "isExternalWebView"
    private const val EVENT_PROVIDER = "eventProvider"
    private const val MAX_KEY_LENGTH = 40
    private const val HEX_MASK = "<hex>"

    private val longHex = Regex("(0x)?[0-9a-fA-F]{40,}")

    // An Asset ID names a public contract, so its address stays.
    private val assetId = Regex("[a-z0-9]+/(mainnet|testnet)/[a-z0-9]+(/[^/\\s]+)?")

    fun params(raw: Map<String, Any?>): MutableMap<String, Any> {
        val params = LinkedHashMap<String, Any>()
        for ((key, value) in raw) {
            if (key.length > MAX_KEY_LENGTH) {
                continue
            }
            when (value) {
                is String -> params[key] = mask(value)
                is Number, is Boolean -> params[key] = value
                else -> Unit
            }
        }
        params[IS_EXTERNAL_WEB_VIEW] = 1
        params[EVENT_PROVIDER] = PROVIDER
        return params
    }

    private fun mask(value: String): String {
        if (assetId.matches(value)) {
            return value
        }
        return longHex.replace(value, HEX_MASK)
    }
}