package com.tonapps.singer.extensions

import android.content.ClipData
import android.content.Context
import com.tonapps.singer.R
import uikit.navigation.Navigation.Companion.navigation

fun Context.copyWithToast(text: String) {
    navigation?.toast(getString(R.string.copied))
    copyToClipboard(text)
}

fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = ClipData.newPlainText("", text)
    clipboard.setPrimaryClip(clip)
}