package com.tonapps.signer.extensions

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PersistableBundle
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import com.tonapps.signer.R
import security.KeyHelper
import uikit.HapticHelper
import uikit.navigation.Navigation.Companion.navigation

fun Context.copyToClipboard(text: String, sensitive: Boolean = false) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = ClipData.newPlainText("", text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val extras = PersistableBundle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clip.description.extras = extras
    }
    clipboard.setPrimaryClip(clip)

    HapticHelper.selection(this)

    navigation?.toast(getString(R.string.copied))
}

fun Context.fromClipboard(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}

fun Context.authorizationRequiredError() {
    navigation?.toast(getString(R.string.authorization_required))
}

fun Context.securePrefs(name: String): SharedPreferences {
    KeyHelper.createIfNotExists(name)

    return EncryptedSharedPreferences.create(
        name,
        name,
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}