package com.tonapps.singer.core

import android.net.Uri

class DeepLink {

    companion object {

        fun isSupported(uri: Uri): Boolean {
            if (uri.scheme != "tonsign") {
                return false
            }
            return !uri.getQueryParameter("body").isNullOrBlank()
        }

        fun isSupported(url: String): Boolean {
            return isSupported(Uri.parse(url))
        }
    }
}