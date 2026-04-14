package ui.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import ui.theme.UIKit
import ui.utils.toRichSpanStyle

fun emptyTextValue(): TextFieldValue {
    return TextFieldValue()
}

fun CharSequence.toTextValue(): TextFieldValue {
    return when(this) {
        is String -> TextFieldValue(
            text = this,
            selection = TextRange(length)
        )
        is AnnotatedString -> TextFieldValue(
            annotatedString = this,
            selection = TextRange(length)
        )
        else -> TextFieldValue(
            text = toString(),
            selection = TextRange(length)
        )
    }
}

@Composable
fun CharSequence.annotated(): AnnotatedString {
    return remember(this) { AnnotatedString(toString()) }
}

fun CharSequence.toAnnotatedString(): AnnotatedString {
    return AnnotatedString(toString())
}

fun AnnotatedString.Builder.withLink(
    text: String,
    link: String,
    style: TextStyle,
    color: Color) {
    val style = style.toRichSpanStyle(color = color)

    withLink(
        LinkAnnotation.Url(
            url = link,
            styles = TextLinkStyles(style)
        )
    ) {
        append(text)
    }
}

@Composable
fun String.toAnnotatedLink(link: String = this): AnnotatedString {
    return buildAnnotatedString {
        withLink(
            LinkAnnotation.Url(
                url = link,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.surfaceTint,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) {
            append(this@toAnnotatedLink)
        }
    }
}

private const val START_LENGTH = 9
private const val END_LENGTH = 6

fun String.shrinkAddress(): String {
    if (length <= START_LENGTH + END_LENGTH) {
        return this
    }

    val start = substring(0, START_LENGTH)
    val end = substring(length - END_LENGTH, length)

    return "$start...$end"
}
