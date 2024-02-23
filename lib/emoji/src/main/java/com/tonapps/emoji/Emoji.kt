package com.tonapps.emoji

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.collection.LruCache
import com.tonapps.emoji.compat.EmojiCompatHelper
import com.tonapps.emoji.ui.drawable.PictogramDrawable
import com.tonapps.emoji.ui.style.NotoStyle
import com.tonapps.emoji.ui.style.DefaultStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

object Emoji {

    val scope = CoroutineScope(Dispatchers.Main)

    private val lruCache = object : LruCache<CharSequence, PictogramDrawable>((Runtime.getRuntime().maxMemory() / 1024).toInt() / 8) {
        override fun sizeOf(key: CharSequence, value: PictogramDrawable): Int {
            if (value.bitmap.isRecycled) {
                remove(key)
                return 0
            }
            return value.bitmap.allocationByteCount
        }
    }

    private val all = Collections.synchronizedList(mutableListOf<EmojiEntity>())
    private val simpleEmojiTypeface = DefaultStyle()

    @Volatile
    private var notoEmojiTypeface: NotoStyle? = null

    private val defaultStyle: DefaultStyle
        get() = notoEmojiTypeface ?: simpleEmojiTypeface

    fun init(context: Context) {
        scope.launch {
            notoEmojiTypeface = NotoStyle.create(context)
            lruCache.evictAll()
        }
    }

    suspend fun get(context: Context): Array<EmojiEntity> {
        if (all.isEmpty()) {
            EmojiCompatHelper.init()
            all.addAll(getAll(context))
        }

        return all.toTypedArray()
    }

    internal suspend fun getDrawable(context: Context, emoji: CharSequence): PictogramDrawable {
        return getDrawableFromCache(emoji) ?: createDrawable(context, emoji)
    }

    private fun getDrawableFromCache(emoji: CharSequence): PictogramDrawable? {
        val drawable = lruCache.get(emoji) ?: return null
        if (drawable.bitmap.isRecycled) {
            lruCache.remove(emoji)
            return null
        }
        drawable.alpha = 255
        return drawable
    }

    private suspend fun createDrawable(context: Context, emoji: CharSequence): PictogramDrawable = withContext(Dispatchers.IO) {
        val tmpBitmap = defaultStyle.draw(emoji)
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tmpBitmap.copy(Bitmap.Config.HARDWARE, false)
        } else {
            tmpBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val drawable = PictogramDrawable(emoji, context, bitmap)
        drawable.alpha = 0
        lruCache.put(emoji, drawable)
        drawable
    }

    private fun getAll(context: Context): List<EmojiEntity> {
        val list = mutableListOf<EmojiEntity>()
        val resId = if (EmojiCompatHelper.is12Supported()) {
            R.array.emoji_by_category_raw_resources_gender_inclusive
        } else {
            R.array.emoji_by_category_raw_resources
        }
        val array = context.resources.obtainTypedArray(resId)
        try {
            for (i in 0 until array.length()) {
                val resourceId = array.getResourceId(i, 0)
                list.addAll(loadEmoji(context, resourceId))
            }
        } catch (ignored: Throwable) {} finally {
            array.recycle()
        }
        return list.toList()
    }

    private fun loadEmoji(context: Context, raw: Int): List<EmojiEntity> {
        return context.resources
            .openRawResource(raw)
            .bufferedReader()
            .useLines { it.toList() }
            .map { EmojiCompatHelper.filterAvailable(it.split(",")) }
            .filter { it.isNotEmpty() }
            .map { EmojiEntity(it.first(), it.drop(1)) }
    }
}