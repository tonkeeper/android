package com.tonapps.tonkeeper.ui.screen.external.qr

import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.ur.URDecoder
import com.tonapps.ur.registry.RegistryItem
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import uikit.navigation.Navigation.Companion.navigation

private fun fixReceivedURPart(part: String): String {
    if (part.startsWith("http://", ignoreCase = true)) {
        return part.removePrefix("http://")
    } else if (part.startsWith("https://", ignoreCase = true)) {
        return part.removePrefix("https://")
    }
    return part
}

@Suppress("UNCHECKED_CAST")
fun <R: RegistryItem> QRCameraScreen.urFlow(): Flow<R> {
    val urDecoder = URDecoder()

    return readerFlow.map { urDecoder.receivePart(fixReceivedURPart(it)) }
        .filter { it }
        .map { urDecoder.result }
        .filter { it.type == com.tonapps.ur.ResultType.SUCCESS }
        .map { it.ur.decodeFromRegistry() as R }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .catch { navigation?.toast(Localization.unknown_error) }
}
