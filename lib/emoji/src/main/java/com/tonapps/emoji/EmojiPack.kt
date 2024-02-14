package com.tonapps.emoji

import android.content.Context
import com.tonapps.emoji.compat.EmojiCompatHelper
import java.util.Collections

object EmojiPack {

    private val all = Collections.synchronizedList(mutableListOf<Emoji>())

    suspend fun get(context: Context): Array<Emoji> {
        if (all.isEmpty()) {
            EmojiCompatHelper.init()
            all.addAll(getAll(context))
        }
        return all.toTypedArray()
    }

    private fun getAll(context: Context): List<Emoji> {
        val list = mutableListOf<Emoji>()
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

    private fun loadEmoji(context: Context, raw: Int): List<Emoji> {
        return context.resources
            .openRawResource(raw)
            .bufferedReader()
            .useLines { it.toList() }
            .map { EmojiCompatHelper.filterAvailable(it.split(",")) }
            .filter { it.isNotEmpty() }
            .map { Emoji(it.first(), it.drop(1)) }
    }

}