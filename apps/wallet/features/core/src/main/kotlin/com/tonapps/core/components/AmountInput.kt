package com.tonapps.core.components

private const val ARABIC_DECIMAL_SEPARATOR = 0x066B
private const val FULLWIDTH_DECIMAL_SEPARATOR = 0xFF0E
private const val UNLIMITED_DECIMALS = Int.MAX_VALUE

fun String.normalizeAmountInput(): String {
    val builder = StringBuilder(length)
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        val digit = Character.digit(codePoint, 10)
        when {
            digit >= 0 -> builder.append('0' + digit)
            codePoint == ARABIC_DECIMAL_SEPARATOR || codePoint == FULLWIDTH_DECIMAL_SEPARATOR -> {
                builder.append('.')
            }
            else -> builder.appendCodePoint(codePoint)
        }
        index += Character.charCount(codePoint)
    }
    return builder.toString()
}

fun String.sanitizeAmountInput(maxDecimals: Int = UNLIMITED_DECIMALS): String {
    val filtered = normalizeAmountInput()
        .filter { it in '0'..'9' || it == '.' || it == ',' }
    if (filtered.isEmpty()) {
        return ""
    }

    val builder = StringBuilder(filtered.length)
    var hasSeparator = false
    var decimalCount = 0
    for (char in filtered) {
        if (char == '.' || char == ',') {
            if (!hasSeparator) {
                hasSeparator = true
                builder.append(char)
            }
        } else {
            if (hasSeparator) {
                if (decimalCount >= maxDecimals) {
                    continue
                }
                decimalCount++
            }
            builder.append(char)
        }
    }

    if (builder.isNotEmpty() && (builder[0] == '.' || builder[0] == ',')) {
        builder.insert(0, '0')
    }

    return builder.toString()
}
