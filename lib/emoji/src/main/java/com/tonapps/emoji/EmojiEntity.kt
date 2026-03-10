package com.tonapps.emoji

import android.icu.lang.UCharacter

data class EmojiEntity(
    val value: CharSequence,
    val variants: List<CharSequence>,
    val noto: Boolean,
    val custom: Boolean
) {

    constructor(
        value: CharSequence,
        variants: List<CharSequence> = emptyList(),
        custom: Boolean
    ): this(value, variants, false, custom)

    val name: CharSequence? by lazy {
        try {
            UCharacter.getName(value.toString(), "")?.ifBlank { null }
        } catch (ignored: Throwable) {
            null
        }
    }
}