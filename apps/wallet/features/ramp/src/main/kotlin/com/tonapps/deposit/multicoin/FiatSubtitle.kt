package com.tonapps.deposit.multicoin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBarSubtitle
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun FiatSubtitle(text: String, fiat: String, flagUri: String?) {
    val accentColor = UIKit.colorScheme.accent.blue
    val start = text.lastIndexOf(fiat)
    if (flagUri != null && start >= 0) {
        MoonTopAppBarSubtitle(text = text.substring(0, start))
        MoonItemImage(
            image = flagUri,
            modifier = Modifier.padding(start = 2.dp, end = 4.dp),
            size = FLAG_SIZE,
        )
        MoonTopAppBarSubtitle(text = accented(text.substring(start), fiat, accentColor))
    } else {
        MoonTopAppBarSubtitle(text = accented(text, fiat, accentColor))
    }
    Spacer(modifier = Modifier.width(4.dp))
    MoonItemIcon(
        painter = painterResource(UIKitIcon.ic_switch_16),
        size = 12.dp,
        color = accentColor,
    )
}

@Composable
private fun accented(text: String, fiat: String, color: Color): CharSequence {
    return remember(text, fiat, color) {
        buildAnnotatedString {
            val start = text.lastIndexOf(fiat)
            if (start < 0) {
                append(text)
            } else {
                append(text.substring(0, start))
                withStyle(SpanStyle(color = color)) {
                    append(fiat)
                }
                append(text.substring(start + fiat.length))
            }
        }
    }
}

private val FLAG_SIZE = 12.dp