package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import android.os.Parcelable
import com.tonapps.core.deeplink.DeepLink
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletAwaitingLink(
    val uri: Uri,
    val fromQR: Boolean,
    val referrer: Uri?,
    val fromExternal: Boolean,
    val fromPackageName: String?,
    val fromBrowser: Boolean,
) : Parcelable {

    fun toDeepLink(): DeepLink {
        return DeepLink(uri, fromQR, referrer, fromExternal)
    }
}
