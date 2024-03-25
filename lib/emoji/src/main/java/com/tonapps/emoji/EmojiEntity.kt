package com.tonapps.emoji

data class EmojiEntity(val value: CharSequence, val variants: List<CharSequence>, val noto: Boolean) {

    constructor(value: CharSequence, variants: List<CharSequence> = emptyList()): this(value, variants, false)
}