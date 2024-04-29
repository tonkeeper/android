package com.tonapps.signer.extensions

import android.os.Bundle
import androidx.core.os.BundleCompat

inline fun <reified R> Bundle.getObject(key: String): R {
    return BundleCompat.getParcelable(this, key, R::class.java)!!
}

inline fun <reified R> Bundle.getObjectOrNull(key: String): R? {
    return try {
        getObject(key)
    } catch (e: Exception) {
        null
    }
}