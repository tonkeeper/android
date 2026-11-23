package com.tonapps.perps.screens.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.tonapps.perps.data.localDecimalSeparator

internal object PerpsDecimalInputTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val localized = AnnotatedString(text.text.replace('.', localDecimalSeparator()))
        return TransformedText(localized, OffsetMapping.Identity)
    }
}
