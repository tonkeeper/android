package ui.components.image

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun TokenImage(
    modifier: Modifier = Modifier,
    icon: String,
    subicon: String? = null,
    size: Int = 0,
    borderColor: Color = UIKit.colorScheme.background.page
) {
    BoxWithConstraints(
        modifier = modifier
    ) {
        val offset = maxWidth / 24

        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            url = icon,
            size = size
        )

        subicon?.let {
            TokenImageBorder(
                modifier = Modifier
                    .fillMaxSize(fraction = 1f / 3f)
                    .align(Alignment.BottomEnd)
                    .offset(x = offset, y = offset),
                icon = it,
                size = size / 3,
                borderColor = borderColor
            )
        }
    }
}

@Composable
fun TokenImageBorder(
    modifier: Modifier = Modifier,
    icon: String,
    size: Int = 0,
    borderSize: Dp = 3.dp,
    borderColor: Color = UIKit.colorScheme.background.page
) {
    Box(
        modifier = modifier
            .border(borderSize, borderColor, CircleShape)
            .padding(borderSize)
            .clip(CircleShape)
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            url = icon,
            size = size
        )
    }
}