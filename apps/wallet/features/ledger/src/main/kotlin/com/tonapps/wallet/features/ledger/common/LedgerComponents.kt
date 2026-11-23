package com.tonapps.wallet.features.ledger.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
internal fun LedgerDeviceHeader(displayText: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        LedgerDevicePicture(
            modifier = Modifier.widthIn(max = DEVICE_MAX_WIDTH),
            displayText = displayText,
        )
    }
}

@Composable
internal fun LedgerMessage(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    reservedLines: Int = 1,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReservedText(
            text = title,
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            reservedLines = reservedLines,
        )
        if (!subtitle.isNullOrBlank() || reservedLines > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            ReservedText(
                text = subtitle.orEmpty(),
                style = UIKit.typography.body1,
                color = if (isError) { UIKit.colorScheme.accent.red } else { UIKit.colorScheme.text.secondary },
                reservedLines = reservedLines,
            )
        }
    }
}

@Composable
private fun ReservedText(text: String, style: TextStyle, color: Color, reservedLines: Int) {
    val lineHeight = with(LocalDensity.current) { if (style.lineHeight.isSp) { style.lineHeight.toDp() } else { 0.dp } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = lineHeight * reservedLines),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun LedgerAddressBox(address: String, modifier: Modifier = Modifier) {
    SelectionContainer(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(UIKit.colorScheme.background.content, UIKit.shapes.large)
            .padding(16.dp),
    ) {
        Text(
            text = address,
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val DEVICE_MAX_WIDTH = 353.dp
