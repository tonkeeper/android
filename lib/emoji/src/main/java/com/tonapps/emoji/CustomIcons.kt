package com.tonapps.emoji

import androidx.annotation.DrawableRes
import java.util.concurrent.ConcurrentHashMap

internal class CustomIcons {

    private companion object {
        private const val PREFIX = "custom_"
    }

    private val store = ConcurrentHashMap<CharSequence, Int>(3, 1.0f, 2)
    private val list = mutableListOf<CharSequence>()

    init {
        set("wallet", R.drawable.ic_label_wallet)
        set("leaf", R.drawable.ic_label_leaf)
        set("lock", R.drawable.ic_label_lock)
        set("key", R.drawable.ic_label_key)
        set("inbox", R.drawable.ic_label_inbox)
        set("snowflake", R.drawable.ic_label_snowflake)
        set("sparkles", R.drawable.ic_label_sparkles)
        set("sub", R.drawable.ic_label_sun)
        set("hare", R.drawable.ic_label_hare)
        set("flash", R.drawable.ic_label_flash)
        set("back_card", R.drawable.ic_label_back_card)
        set("gear", R.drawable.ic_label_gear)
        set("hand_raised", R.drawable.ic_label_hand_raised)
        set("magnifying_glass_circle", R.drawable.ic_label_magnifying_glass_circle)
        set("flash_circle", R.drawable.ic_label_flash_circle)
        set("dollar_circle", R.drawable.ic_label_dollar_circle)
        set("euro_circle", R.drawable.ic_label_euro_circle)
        set("sterling_circle", R.drawable.ic_label_sterling_circle)
        set("chinese_yuan_circle", R.drawable.ic_label_chinese_yuan_circle)
        set("ruble_circle", R.drawable.ic_label_ruble_circle)
        set("indian_rupee_circle", R.drawable.ic_label_indian_rupee_circle)
    }

    operator fun set(
        emoji: CharSequence,
        @DrawableRes resId: Int
    ) {
        list.add("$PREFIX$emoji")
        store[emoji] = resId
    }

    fun contains(emoji: CharSequence): Boolean {
        return store.containsKey(emoji)
    }

    fun getAll(): List<CharSequence> = list.toList()

    operator fun get(emoji: CharSequence): Int? {
        return if (emoji.startsWith(PREFIX)) {
            store[emoji.removePrefix(PREFIX)]
        } else {
            store[emoji]
        }
    }
}