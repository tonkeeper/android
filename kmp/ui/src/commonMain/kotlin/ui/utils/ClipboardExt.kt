package ui.utils

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.input.TextFieldValue
import ui.text.toTextValue

fun ClipboardManager.getTextValue(): TextFieldValue? {
    return getText()?.toTextValue()
}
