package com.tonapps.tonkeeper.core.tonconnect.models

import android.net.Uri
import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class TCData(
    val manifest: TCManifest,
    val accountId: String,
    val testnet: Boolean,
    val clientId: String,
    val items: List<TCItem>
): Parcelable {

    val shortAddress: String
        get() = accountId.toUserFriendly(testnet = testnet).shortAddress

    val url: String
        get() = manifest.url

    val host: String
        get() = Uri.parse(url).host!!
}