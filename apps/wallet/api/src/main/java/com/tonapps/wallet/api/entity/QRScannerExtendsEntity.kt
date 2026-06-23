package com.tonapps.wallet.api.entity

import android.os.Parcelable
import com.tonapps.log.L
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Parcelize
data class QRScannerExtendsEntity(
    val version: Int,
    val regexp: String,
    val url: String
): Parcelable {

    @IgnoredOnParcel
    val regex: Regex by lazy {
        Regex(regexp)
    }

    constructor(json: JSONObject) : this(
        version = json.getInt("version"),
        regexp = json.getString("regexp"),
        url = json.getString("url")
    )

    fun isMatch(input: String): Boolean {
        return regex.containsMatchIn(input)
    }

    fun buildUrl(input: String): String? {
        if (!isMatch(input)) {
            return null
        }
        val encoded = URLEncoder.encode(input, StandardCharsets.UTF_8.name())
        return url.replace("{{QR_CODE}}", encoded)
    }

    companion object {

        fun of(array: JSONArray): List<QRScannerExtendsEntity> {
            return (0 until array.length()).mapNotNull {
                val json = array.getJSONObject(it)
                if (json.getInt("version") == 1) {
                    QRScannerExtendsEntity(array.getJSONObject(it))
                } else {
                    null
                }
            }
        }
    }
}