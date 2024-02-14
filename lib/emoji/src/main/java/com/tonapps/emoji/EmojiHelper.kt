package com.tonapps.emoji

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import java.io.File
import java.util.Hashtable
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object EmojiHelper {

    val uiHandler = HandlerCompat.createAsync(Looper.getMainLooper())

    private val typefaceCache = Hashtable<String, Typeface>()

    fun createBackgroundPriorityExecutor(name: String): ThreadPoolExecutor {
        val threadFactory = ThreadFactory { runnable ->
            val thread = Thread(runnable, name)
            thread.priority = Thread.MIN_PRIORITY
            thread
        }
        val executor = ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, LinkedBlockingDeque(), threadFactory)
        executor.allowCoreThreadTimeOut(true)
        return executor
    }

    fun getLocalNotoEmoji(): Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return getFileFont("/system/fonts/NotoColorEmoji.ttf")
    }

    suspend fun getCloudNotoEmoji(context: Context): Typeface? = suspendCoroutine {
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
        FontsContractCompat.requestFont(context, request, callback, uiHandler)
    }

    private fun getFileFont(pathname: String): Typeface? {
        synchronized (typefaceCache) {
            if (typefaceCache.containsKey(pathname)) {
                return typefaceCache[pathname]
            }
            try {
                val file = File(pathname)
                if (!file.exists()) {
                    return null
                }
                val typeface = Typeface.createFromFile(file)
                typefaceCache[pathname] = typeface
                return typeface
            } catch (e: Throwable) {
                return null
            }
        }
    }

}