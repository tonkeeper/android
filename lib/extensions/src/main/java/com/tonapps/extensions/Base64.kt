package com.tonapps.extensions

import android.util.Base64

val String.base64: ByteArray
    get() = Base64.decode(this, Base64.DEFAULT)

val ByteArray.base64: String
    get() = Base64.encodeToString(this, Base64.DEFAULT)