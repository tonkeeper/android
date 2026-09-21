package com.tonapps.tonkeeper.ui.screen.external.qr

import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.ur.ResultType
import com.tonapps.ur.URDecoder
import com.tonapps.ur.registry.RegistryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull

// Bounds the memory a stream of distinct broken codes can take; older keys may be reported twice.
private const val MAX_REPORTED_FAILURES = 16

private fun fixReceivedURPart(part: String): String {
    if (part.startsWith("http://", ignoreCase = true)) {
        return part.removePrefix("http://")
    } else if (part.startsWith("https://", ignoreCase = true)) {
        return part.removePrefix("https://")
    }
    return part
}

private fun <R: RegistryItem> decodeRegistryItem(
    result: URDecoder.Result,
    type: Class<R>
): Result<R> = runCatching {
    if (result.type != ResultType.SUCCESS) {
        throw IllegalArgumentException(result.error ?: "Failed to assemble UR")
    }
    val item = result.ur.decodeFromRegistry()
        ?: throw IllegalArgumentException("Unsupported UR type: ${result.ur.type}")
    if (!type.isInstance(item)) {
        throw IllegalArgumentException("Unexpected UR type: ${result.ur.type}")
    }
    type.cast(item)
}

/**
 * Identifies the code a failed scan came from: the assembled UR when there is one, its error
 * otherwise. UR values compare by type and payload, so every pass over the same code maps to the
 * same key.
 */
private fun failureKey(result: URDecoder.Result): Any = result.ur ?: "assemble:${result.error}"

/**
 * Emits registry items of [type] scanned by the camera.
 *
 * Scanning a broken or mismatched code is reported through [onError] instead of failing the flow,
 * because a decoder that already produced a result ignores every following part; it is replaced so
 * that the next code can still be scanned. A code stays in front of the camera though, and an
 * animated one reassembles on every fountain cycle, so failures are reported once per code rather
 * than once per pass. [onError] is called on the collector dispatcher.
 */
fun <R: RegistryItem> QRCameraScreen.urFlow(
    type: Class<R>,
    onError: suspend (Throwable) -> Unit = {}
): Flow<R> {
    var urDecoder = URDecoder()
    val reportedFailures = LinkedHashSet<Any>()

    return readerFlow.mapNotNull { part ->
        if (!urDecoder.receivePart(fixReceivedURPart(part))) {
            return@mapNotNull null
        }
        val result = urDecoder.result ?: return@mapNotNull null
        val decoded = decodeRegistryItem(result, type)
        if (decoded.isFailure) {
            urDecoder = URDecoder()
            if (!reportedFailures.add(failureKey(result))) {
                return@mapNotNull null
            }
            if (reportedFailures.size > MAX_REPORTED_FAILURES) {
                reportedFailures.remove(reportedFailures.first())
            }
        }
        decoded
    }.flowOn(Dispatchers.IO).mapNotNull { decoded ->
        decoded.getOrElse {
            onError(it)
            null
        }
    }
}
