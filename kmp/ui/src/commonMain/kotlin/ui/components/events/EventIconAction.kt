package ui.components.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.size.Size
import ui.components.base.UIKitProgressIndicator
import ui.components.image.AsyncImage
import ui.theme.UIKit

@Composable
internal fun EventIconAction(
    action: UiEvent.Item.Action
) {

    val iconSize = 44.dp
    val colorScheme = UIKit.colorScheme

    val iconUrl = action.iconUrl
    val pictureUrl = if (!action.spam) action.imageUrl else null
    val showLoader = action.state == UiEvent.Item.Action.State.Pending

    Layout(
        modifier = Modifier.drawBehind {
            drawCircle(
                color = colorScheme.background.contentTint,
                radius = size.minDimension / 2f,
            )
        },
        content = {
            when {
                pictureUrl != null -> {
                    AsyncImage(
                        modifier = Modifier.clip(CircleShape),
                        url = pictureUrl,
                        size = 128,
                        contentScale = ContentScale.Crop
                    )
                }
                iconUrl != null -> {
                    AsyncImage(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colorScheme.background.contentTint)
                            .padding(8.dp),
                        url = iconUrl,
                        size = Size.ORIGINAL,
                        contentScale = ContentScale.Inside,
                        colorFilter = colorScheme.icon.secondaryColorFilter,
                        crossfadeDuration = 0
                    )
                }
            }
            if (showLoader) {
                UIKitProgressIndicator()
            }
        }
    ) { measurables, _ ->
        val sizePx = iconSize.roundToPx()
        val fixed = Constraints.fixed(sizePx, sizePx)
        val contentPlaceable = measurables.getOrNull(0)?.measure(fixed)
        val loaderPlaceable = if (showLoader) {
            measurables.getOrNull(1)?.measure(Constraints())
        } else null

        layout(sizePx, sizePx) {
            contentPlaceable?.place(0, 0)
            loaderPlaceable?.place(
                x = (-4).dp.roundToPx(),
                y = (-4).dp.roundToPx()
            )
        }
    }
}