package ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit

fun TextStyle.toRichSpanStyle(
    color: Color = this.color,
    fontSize: TextUnit = this.fontSize,
    fontWeight: FontWeight? = this.fontWeight,
    fontStyle: FontStyle? = this.fontStyle,
    fontFamily: FontFamily? = this.fontFamily,
    letterSpacing: TextUnit = this.letterSpacing,
    textDecoration: TextDecoration? = this.textDecoration,
    background: Color = this.background,
    baselineShift: BaselineShift? = this.baselineShift,
    fontSynthesis: FontSynthesis? = this.fontSynthesis,
    fontFeatureSettings: String? = this.fontFeatureSettings,
): SpanStyle = SpanStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontStyle = fontStyle,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    background = background,
    baselineShift = baselineShift,
    fontSynthesis = fontSynthesis,
    fontFeatureSettings = fontFeatureSettings,
)
