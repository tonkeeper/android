package com.tonapps.emoji.compat

import android.os.Build
import android.text.TextPaint
import androidx.core.graphics.PaintCompat
import androidx.core.os.CancellationSignal
import androidx.emoji2.text.EmojiCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object EmojiCompatHelper {

    @Volatile
    private var loaded = false

    private const val VARIATION_SELECTOR = "\uFE0F"
    private const val YAWNING_FACE_EMOJI = "\uD83E\uDD71"
    private val paint = TextPaint()
    private val CATEGORY_MOVED_EMOJIS = listOf("\u2695\uFE0F", "\u2640\uFE0F", "\u2642\uFE0F", "\u265F\uFE0F", "\u267E\uFE0F")

    suspend fun init() {
        loaded = try {
            compatInit()
        } catch (ignored: Throwable) {
            false
        }
    }

    fun process(emoji: CharSequence): CharSequence {
        if (!loaded) {
            return emoji
        }
        return EmojiCompat.get().process(emoji) ?: emoji
    }

    fun filterAvailable(emojiList: List<String>): List<String> {
        return emojiList.filter {
            isAvailableRender(it)
        }.toList()
    }

    fun is12Supported() = isAvailableRender(YAWNING_FACE_EMOJI)

    private fun isAvailableRender(emoji: String): Boolean {
        return if (loaded) {
            EmojiCompat.get().getEmojiMatch(emoji, Int.MAX_VALUE) == EmojiCompat.EMOJI_SUPPORTED
        } else {
            getClosest(emoji) != null
        }
    }

    private fun getClosest(emoji: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return emoji.replace(VARIATION_SELECTOR, "").takeIfHasGlyph()
        }
        return emoji.takeIfHasGlyph() ?: run {
            if (CATEGORY_MOVED_EMOJIS.contains(emoji)) {
                emoji.replace(VARIATION_SELECTOR, "").takeIfHasGlyph()
            } else {
                null
            }
        }
    }

    private fun String.takeIfHasGlyph() = takeIf { PaintCompat.hasGlyph(paint, this) }

    private suspend fun compatInit(): Boolean = suspendCancellableCoroutine { continuation ->
        val canceller = CancellationSignal()
        continuation.invokeOnCancellation { canceller.cancel() }

        if (EmojiCompat.isConfigured()) {
            when (EmojiCompat.get().loadState) {
                EmojiCompat.LOAD_STATE_SUCCEEDED -> {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                EmojiCompat.LOAD_STATE_LOADING, EmojiCompat.LOAD_STATE_DEFAULT -> {
                    EmojiCompat.get().registerInitCallback(object : EmojiCompat.InitCallback() {

                        override fun onInitialized() {
                            super.onInitialized()
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }
                        }

                        override fun onFailed(throwable: Throwable?) {
                            super.onFailed(throwable)
                            if (continuation.isActive) {
                                continuation.resumeWithException(throwable ?: Exception("EmojiCompat failed to initialize"))
                            }
                        }
                    })
                }
                EmojiCompat.LOAD_STATE_FAILED -> {
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("EmojiCompat failed to initialize"))
                    }
                }
            }
        } else {
            if (continuation.isActive) {
                continuation.resumeWithException(Exception("EmojiCompat is not configured"))
            }
        }
    }
}