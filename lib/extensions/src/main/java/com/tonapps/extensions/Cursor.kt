package com.tonapps.extensions

import android.database.Cursor

fun Cursor?.isNullOrEmpty(): Boolean = this == null || this.count == 0

fun Cursor.closeSafe() {
    if (!this.isClosed) {
        this.close()
    }
}