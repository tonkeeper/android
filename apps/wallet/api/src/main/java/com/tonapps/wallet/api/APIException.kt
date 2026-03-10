package com.tonapps.wallet.api

import android.net.Uri

sealed class APIException(message: String?, cause: Throwable?): Throwable(message, cause) {

    private companion object {

        private fun buildMessage(vararg lines: String?): String {
            return lines.filterNotNull().joinToString(separator = ";\n")
        }
    }

    class Emulation(boc: String, sourceUri: Uri? = null, cause: Throwable? = null): APIException(
        message = buildMessage("Source URI: $sourceUri", "Boc: $boc"),
        cause = cause
    )
}