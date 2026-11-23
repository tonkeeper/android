package com.tonapps.core.components

data class AmountToggle(
    val assetId: String,
    val sourceText: String,
    val toggledText: String,
    val toFiat: Boolean,
    val sourceIsMax: Boolean = false,
    val toggledIsMax: Boolean = false,
) {

    fun toggledBack(assetId: String, text: String, toFiat: Boolean, isMax: Boolean = false): AmountToggle? {
        if (this.assetId != assetId || toggledText != text || this.toFiat == toFiat) {
            return null
        }
        return AmountToggle(
            assetId = assetId,
            sourceText = text,
            toggledText = sourceText,
            toFiat = toFiat,
            sourceIsMax = isMax,
            toggledIsMax = sourceIsMax,
        )
    }
}
