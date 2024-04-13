package com.tonapps.emoji.ui.style

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.tonapps.emoji.R
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class NotoStyle(typeface: Typeface) : DefaultStyle(typeface) {

    override fun draw(canvas: Canvas, value: CharSequence) {
        canvas.translate(0f, canvas.height / 16f)
        super.draw(canvas, value)
    }

    companion object {

        suspend fun create(context: Context): NotoStyle? {
            val typeface = getTypeface(context) ?: return null
            return NotoStyle(typeface)
        }

        suspend fun getTypeface(context: Context): Typeface? {
            val systemFont = getLocalTypeface()
            if (systemFont != null) {
                return systemFont
            }
            return getCloudTypeface(context)
        }

        private fun getLocalTypeface(): Typeface? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return null
            }
            try {
                val file = File("/system/fonts/NotoColorEmoji.ttf")
                if (!file.exists()) {
                    return null
                }
                return Typeface.createFromFile(file)
            } catch (e: Throwable) {
                return null
            }
        }

        private suspend fun getCloudTypeface(context: Context): Typeface? = suspendCoroutine {
            val request = FontRequest(
                "com.google.android.gms.fonts",
                "com.google.android.gms",
                "Noto Color Emoji Compat",
                R.array.com_google_android_gms_fonts_certs
            )
            val callback = object : FontsContractCompat.FontRequestCallback() {
                override fun onTypefaceRetrieved(typeface: Typeface) {
                    it.resume(typeface)
                }

                override fun onTypefaceRequestFailed(reason: Int) {
                    it.resume(null)
                }
            }
            FontsContractCompat.requestFont(context, request, callback, Handler(Looper.getMainLooper()))
        }
    }
}