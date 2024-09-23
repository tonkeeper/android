package com.tonapps.tonkeeper.manager.tonconnect

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import com.tonapps.security.Security
import com.tonapps.security.hex
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class TonConnect(
    val clientId: String,
    val request: ConnectRequest,
    val returnUri: Uri?,
    val fromQR: Boolean,
    val jsInject: Boolean,
): Parcelable {

    val proofPayload: String?
        get() = request.proofPayload

    val manifestUrl: String
        get() = request.manifestUrl

    companion object {

        fun fromJsInject(request: ConnectRequest): TonConnect {
            return TonConnect(
                clientId = hex(Security.randomBytes(16)),
                request = request,
                returnUri = null,
                fromQR = false,
                jsInject = true
            )
        }

        private fun isValidClientId(clientId: String?): Boolean {
            return !clientId.isNullOrBlank() && clientId.length == 64
        }

        private fun parseReturn(
            value: String?,
            refSource: Uri?
        ): Uri? {
            return if (value.isNullOrBlank() || value.equals("back", ignoreCase = true)) {
                refSource
            } else if (value.equals("none", ignoreCase = true)) {
                null
            } else {
                try {
                    value.toUri()
                } catch (e: Exception) {
                    throw TonConnectException.ReturnParsingError(value)
                }
            }
        }

        fun parse(
            uri: Uri,
            refSource: Uri?,
            fromQR: Boolean
        ): TonConnect {
            val version = uri.getQueryParameter("v")?.toIntOrNull() ?: 0
            if (version != 2) {
                throw TonConnectException.UnsupportedVersion(version)
            }

            val clientId = uri.getQueryParameter("id")
            if (!isValidClientId(clientId)) {
                throw TonConnectException.WrongClientId(clientId)
            }

            val request = ConnectRequest.parse(uri.getQueryParameter("r"))

            val ret = parseReturn(uri.getQueryParameter("ret"), refSource)

            return TonConnect(
                clientId = clientId!!,
                request = request,
                returnUri = ret,
                fromQR = fromQR,
                jsInject = false
            )
        }
    }
}