package ui.components.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ui.ComposeIcon
import ui.components.SpoilerParticles
import ui.components.image.AsyncImage
import ui.platformNestedScrollInterop
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun TKDetailsLine(
    key: CharSequence,
    value: CharSequence,
    iconLeft: ComposeIcon?,
    spoiler: Boolean,
) {
    if (value.length > 24) {
        TKDetailsLineVertical(
            key = key,
            value = value
        )
    } else {
        TKDetailsLineHorizontal(
            key = key,
            value = value,
            iconLeft = iconLeft,
            spoiler = spoiler
        )
    }
}

@Composable
fun TKDetailsLineHorizontal(
    key: CharSequence,
    value: CharSequence,
    iconLeft: ComposeIcon?,
    spoiler: Boolean,
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key.toString(),
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1
        )
        if (spoiler && iconLeft != null) {
            AsyncImage(
                modifier = Modifier
                    .padding(top = 3.dp, start = 5.dp)
                    .size(16.dp),
                url = iconLeft.url,
                colorFilter = iconLeft.colorFilter
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (spoiler) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .fillMaxWidth(.7f),
            ) {
                SpoilerParticles(
                    modifier = Modifier
                        .fillMaxSize(),
                    particleCount = 1200,
                    edgeFeather = 4.dp,
                    color = UIKit.colorScheme.text.primary
                )
            }
        } else {
            iconLeft?.let {
                AsyncImage(
                    modifier = Modifier
                        .padding(top = 1.dp, end = 4.dp)
                        .size(16.dp),
                    url = it.url,
                    colorFilter = it.colorFilter
                )
            }

            Text(
                text = value.toString(),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary
            )
        }
    }
}

@Composable
private fun TKDetailsLineVertical(
    key: CharSequence,
    value: CharSequence,
) {
    Text(
        text = key.toString(),
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.secondary,
        maxLines = 1
    )

    TKDetailsSubtitleLineVertical(
        text = value.toString()
    )
}

@Composable
private fun TKDetailsSubtitleLineVertical(text: String) {
    if (text.isBlank()) {
        return
    }

    val gradientSizePx = with(LocalDensity.current) { 16.dp.toPx() }
    val gradientColor = UIKit.colorScheme.background.content
    val scrollState = rememberScrollState()
    Text(
        modifier = Modifier
            .heightIn(max = 120.dp)
            .fillMaxWidth()
            .platformNestedScrollInterop()
            .clipToBounds()
            .drawWithCache {
                val topBrush = Brush.verticalGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    startY = 0f,
                    endY = gradientSizePx
                )
                val bottomBrush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, gradientColor),
                    startY = size.height - gradientSizePx,
                    endY = size.height
                )
                onDrawWithContent {
                    drawContent()
                    if (scrollState.value > 0) {
                        drawRect(
                            brush = topBrush,
                            size = Size(width = size.width, height = gradientSizePx)
                        )
                    }
                    if (scrollState.value < scrollState.maxValue) {
                        drawRect(
                            brush = bottomBrush,
                            topLeft = Offset(x = 0f, y = size.height - gradientSizePx),
                            size = Size(width = size.width, height = gradientSizePx)
                        )
                    }
                }
            }
            .verticalScroll(scrollState),
        text = text,
        style = UIKit.typography.label1,
        color = UIKit.colorScheme.text.primary
    )
}