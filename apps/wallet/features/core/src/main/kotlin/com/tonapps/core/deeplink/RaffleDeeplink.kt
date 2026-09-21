package com.tonapps.core.deeplink

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.wallet.api.entity.StoryEntity

private const val RAFFLE_DEEPLINK_PREFIX = "tonkeeper://raffle"

fun isRaffleDeeplink(payload: String): Boolean =
    DeepLinkRoute.resolve(payload.toUri()) is DeepLinkRoute.Raffle

fun String.withRaffleSourceWalletId(walletId: String): String {
    val uri = toUri()
    if (DeepLinkRoute.resolve(uri) !is DeepLinkRoute.AddWallet) {
        return this
    }
    if (uri.getQueryParameter(DeepLinkRoute.AddWallet.RAFFLE_SOURCE_WALLET_ID_QUERY) != null) {
        return this
    }
    return uri.buildUpon()
        .appendQueryParameter(DeepLinkRoute.AddWallet.RAFFLE_SOURCE_WALLET_ID_QUERY, walletId)
        .build()
        .toString()
}

fun StoryEntity.Stories.withRaffleSourceWalletId(walletId: String): StoryEntity.Stories =
    copy(
        list = list.map { story ->
            val button = story.button ?: return@map story
            story.copy(button = button.copy(payload = button.payload.withRaffleSourceWalletId(walletId)))
        },
    )

fun String.withRaffleSource(source: String): String {
    if (!startsWith(RAFFLE_DEEPLINK_PREFIX)) {
        return this
    }
    val rest = substring(RAFFLE_DEEPLINK_PREFIX.length)
    if (rest.isNotEmpty() && rest.first() != '/' && rest.first() != '?') {
        return this
    }
    val query = substringAfter('?', "")
    if (query.split('&').any { it.startsWith("source=") }) {
        return this
    }
    val separator = if (contains('?')) "&" else "?"
    return "$this${separator}source=${Uri.encode(source)}"
}
