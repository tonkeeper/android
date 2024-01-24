package com.tonapps.singer.extensions

import android.content.ClipData
import android.content.Context
import com.tonapps.singer.R
import uikit.HapticHelper
import uikit.navigation.Navigation.Companion.navigation

fun Context.copyToClipboard(text: String) {
    navigation?.toast(getString(R.string.copied))

    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = ClipData.newPlainText("", text)
    clipboard.setPrimaryClip(clip)

    HapticHelper.selection(this)
}

fun Context.fromClipboard(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}