package core.extensions

import android.net.Uri

fun Uri.getQuery(vararg keys: String): String? {
    for (key in keys) {
        val value = getQueryParameter(key)
        if (value.isNullOrBlank()) {
            continue
        }
        return value
    }
    return null
}
