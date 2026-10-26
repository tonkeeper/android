package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import android.content.Context
import androidx.annotation.RawRes
import com.tonapps.async.Async
import com.tonapps.extensions.rawText
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Script
import kotlinx.coroutines.withContext

internal class RawInAppScript(
    context: Context,
    @RawRes private val id: Int,
) : Script {

    private val context = context.applicationContext

    override suspend fun load(): String = withContext(Async.Io) {
        context.rawText(id)
    }
}