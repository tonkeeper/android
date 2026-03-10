package com.tonapps.wallet.data.collectibles.entities

import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.toRawAddress
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class DnsExpiringEntity(
    val expiringAt: Long,
    val name: String,
    val dnsItem: NftEntity? = null
): Parcelable {

    @IgnoredOnParcel
    val addressRaw: String by lazy {
        dnsItem?.address?.toRawAddress() ?: ""
    }

    @IgnoredOnParcel
    val inSale: Boolean by lazy {
        dnsItem?.inSale ?: false
    }

    @IgnoredOnParcel
    val daysUntilExpiration: Int by lazy {
        val currentTime = System.currentTimeMillis() / 1000
        val remainingSeconds = expiringAt - currentTime

        if (remainingSeconds <= 0) {
            0
        } else {
            (remainingSeconds / (24 * 60 * 60)).toInt()
        }
    }

}