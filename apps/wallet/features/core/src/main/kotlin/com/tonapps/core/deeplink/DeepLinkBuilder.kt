package com.tonapps.core.deeplink

import android.net.Uri
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.extensions.toUriOrNull

object DeepLinkBuilder {

    private const val prefix = "https://app.tonkeeper.com/"
    private const val appScheme = "tonkeeper://"

    private val dAppIsSpecialHosts = arrayOf(
        "tonviewer.com",
        "tronscan.org",
        "testnet.tonviewer.com",
        "test.tronscan.org",
        "eth.blockscout.com",
        "arbitrum.blockscout.com",
        "base.blockscout.com",
        "bscscan.com",
        "mempool.space",
    )

    // Normalizes a raw input (e.g. a scanned QR) into a deeplink uri: a bare crypto
    // address is wrapped as a send deeplink, otherwise it is parsed as-is.
    fun preprocess(value: String): Uri? {
        if (value.isBlank()) {
            return null
        }

        value.toUriOrNull()
            ?.let { return it }

        plainAddressOrNull(value)?.let { address ->
            return Uri.parse("tonkeeper://send")
                .buildUpon()
                .appendQueryParameter("address", address)
                .build()
        }
        return null
    }

    fun plainAddressOrNull(value: String): String? {
        val address = dropAmountOnlyQuery(value) ?: return null
        return address.takeIf { Address.findChainsByAddress(it).isNotEmpty() }
    }

    fun dropAmountOnlyQuery(value: String): String? {
        val query = value.substringAfter('?', missingDelimiterValue = "")
        val hasOtherParams = query.split('&').any { param ->
            param.isNotEmpty() && param.substringBefore('=') != "amount"
        }
        return value.substringBefore('?').takeUnless { hasOtherParams }
    }

    fun dAppIsSpecialUrl(appUrl: String): Boolean {
        val uri = appUrl.toUriOrNull() ?: return false
        return dAppIsSpecialUrl(uri)
    }

    fun dAppIsSpecialUrl(appUri: Uri): Boolean {
        val host = appUri.host ?: return false
        return dAppIsSpecialHosts.any { host.contains(it) }
    }

    /** In-app route to the raffle screen; parsed by [DeepLinkRoute.Raffle]. */
    fun raffle(raffleId: String): String {
        return "${appScheme}raffle/${Uri.encode(raffleId)}"
    }

    fun dApp(appUrl: String): String {
        return "${appScheme}dapp/$appUrl"
    }

    fun dAppShare(appUrl: String): String {
        if (dAppIsSpecialUrl(appUrl)) {
            return appUrl
        }
        return "${prefix}dapp/${Uri.encode(appUrl)}"
    }
}