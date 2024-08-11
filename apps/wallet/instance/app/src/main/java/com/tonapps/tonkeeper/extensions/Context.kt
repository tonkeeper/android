package com.tonapps.tonkeeper.extensions

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.ui.screen.action.ActionScreen
import com.tonapps.tonkeeper.ui.screen.ledger.proof.LedgerProofScreen
import com.tonapps.tonkeeper.ui.screen.ledger.sign.LedgerSignScreen
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import uikit.navigation.Navigation.Companion.navigation
import java.math.BigInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun Context.showToast(@StringRes resId: Int) {
    navigation?.toast(resId)
}

fun Context.copyWithToast(text: String, color: Int = backgroundContentTintColor) {
    navigation?.toast(getString(Localization.copied), color)
    copyToClipboard(text)
}

fun Context.clipboardText(): String {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = clipboard.primaryClip
    val text = clip?.getItemAt(0)?.text ?: ""
    return text.toString()
}

fun Context.copyToClipboard(uri: Uri) {
    copyToClipboard(uri.toString())
}

fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = ClipData.newPlainText("", text)
    clipboard.setPrimaryClip(clip)
}

fun Context.hasPushPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

@ColorInt
fun Context.getDiffColor(diff: String): Int {
    return when {
        diff.startsWith("-") -> accentRedColor
        diff.startsWith("−") -> accentRedColor
        diff.startsWith("+") -> accentGreenColor
        else -> textSecondaryColor
    }
}

fun Context.buildRateString(rate: CharSequence, diff24h: String): CharSequence {
    if (diff24h.isEmpty() || diff24h == "0" || diff24h == "0.00%") {
        return SpannableString(rate)
    }
    val builder = SpannableStringBuilder()
    builder.append(rate)
    builder.append(" ")
    builder.append(diff24h)

    builder.setSpan(
        ForegroundColorSpan(getDiffColor(diff24h)),
        rate.length,
        rate.length + diff24h.length + 1,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return builder
}

fun Context.getStringCompat(@StringRes resId: Int, vararg formatArgs: CharSequence?): CharSequence {
    return getString(resId).formatCompat(*formatArgs)
}

suspend fun Context.signLedgerProof(
    domain: String,
    timestamp: BigInteger,
    payload: String,
    walletId: String,
): ByteArray? = withContext(Dispatchers.Main) {
    suspendCoroutine { continuation ->
        val requestKey = "ledger_sign_request_${domain}"

        navigation?.setFragmentResultListener(requestKey) { bundle ->
            val result = bundle.getByteArray(LedgerProofScreen.SIGNED_PROOF)
            continuation.resume(result)
        }

        navigation?.add(LedgerProofScreen.newInstance(domain, timestamp, payload, walletId, requestKey))
    }
}

suspend fun Context.signLedgerTransaction(transaction: Transaction, walletId: String): Cell? =
    withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            val requestKey = "ledger_sign_request_${transaction.hashCode()}"

            navigation?.setFragmentResultListener(requestKey) { bundle ->
                val result = bundle.getByteArray(LedgerSignScreen.SIGNED_MESSAGE)
                if (result == null) {
                    continuation.resume(null)
                } else {
                    try {
                        continuation.resume(BagOfCells(result).first())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            navigation?.add(LedgerSignScreen.newInstance(transaction, walletId, requestKey))
        }
    }
