package com.tonapps.tonkeeper.extensions

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel

fun RootViewModel.routeToHistoryTab(from: String) {
    val uri = "tonkeeper://history?from=$from".toUri()
    processDeepLink(uri, false, Uri.EMPTY, false, context.packageName)
}