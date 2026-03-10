package com.tonapps.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.tonapps.async.Async
import com.tonapps.emoji.compat.EmojiCompatHelper
import com.tonapps.emoji.ui.drawable.PictogramDrawable
import com.tonapps.emoji.ui.style.DefaultStyle
import com.tonapps.lib.emoji.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object Emoji {

    val scope = Async.mainScope()

    const val WALLET_ICON = "custom_wallet"

    private val all = Collections.synchronizedList(mutableListOf<EmojiEntity>())
    private val simpleEmojiTypeface = DefaultStyle()
    private val customIcons = CustomIcons()
    private val emojiPattern = """^(\p{So})""".toRegex()

    suspend fun findByName(context: Context, query: String): List<EmojiEntity> {
        if (query.isBlank()) {
            return listOf()
        }
        return get(context).filter {
            if (it.custom) {
                false
            } else {
                val name = it.name ?: return@filter false
                name.contains(query, true)
            }
        }
    }

    suspend fun findByNames(context: Context, query: List<String>): List<EmojiEntity> {
        if (query.isEmpty()) {
            return emptyList()
        }
        return query.map {
            findByName(context, it)
        }.flatten().distinct()
    }

    fun getEmojiFromPrefix(text: String): String? {
        val matchResult = emojiPattern.find(text)
        return matchResult?.value
    }

    suspend fun get(context: Context): Array<EmojiEntity> {
        if (all.isEmpty()) {
            EmojiCompatHelper.init()
            all.addAll(getAll(context))
        }

        return all.toTypedArray()
    }

    fun isCustomIcon(
        emoji: CharSequence
    ): Boolean {
        return customIcons.contains(emoji)
    }

    fun getCustomEmoji(emoji: CharSequence) = customIcons[emoji]

    suspend fun isValid(
        context: Context,
        value: CharSequence
    ): Boolean {
        if (customIcons.contains(value)) {
            return true
        }
        val emojis = get(context)
        return emojis.any { it.value == value }
    }

    suspend fun getBitmap(
        context: Context,
        emoji: CharSequence,
        tintColor: Int
    ): Bitmap {
        return getDrawable(context, emoji, tintColor).bitmap
    }

    internal suspend fun getDrawable(
        context: Context,
        emoji: CharSequence,
        tintColor: Int
    ): PictogramDrawable {
        return createDrawable(context, emoji, tintColor)
    }

    private suspend fun createDrawable(
        context: Context,
        emoji: CharSequence,
        tintColor: Int
    ): PictogramDrawable = withContext(Dispatchers.IO) {
        val customIcon = customIcons[emoji]
        if (customIcon == null) {
            drawEmoji(context, emoji)
        } else {
            drawCustomIcon(context, emoji, customIcon, tintColor)
        }
    }

    private fun drawCustomIcon(
        context: Context,
        emoji: CharSequence,
        @DrawableRes resId: Int,
        tintColor: Int
    ): PictogramDrawable {
        val drawable = AppCompatResources.getDrawable(context, resId)!!.mutate()
        if (tintColor != Color.TRANSPARENT) {
            drawable.setTint(tintColor)
        }
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return PictogramDrawable(emoji, context, bitmap)
    }

    private fun drawEmoji(
        context: Context,
        emoji: CharSequence,
    ): PictogramDrawable {
        val tmpBitmap = simpleEmojiTypeface.draw(emoji)
        /*val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tmpBitmap.copy(Bitmap.Config.HARDWARE, false)
        } else {
            tmpBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }*/
        val bitmap = tmpBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val drawable = PictogramDrawable(emoji, context, bitmap)
        drawable.alpha = 0
        return drawable
    }

    private fun getAll(context: Context): List<EmojiEntity> {
        val list = mutableListOf<EmojiEntity>()
        list.addAll(customIcons.getAll().map {
            EmojiEntity(value = it, variants = emptyList(), custom = true)
        })
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
            .map { EmojiEntity(value = it.first(), variants = it.drop(1), custom = false) }
    }
}