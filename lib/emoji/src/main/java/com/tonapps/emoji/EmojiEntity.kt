package com.tonapps.emoji

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
}