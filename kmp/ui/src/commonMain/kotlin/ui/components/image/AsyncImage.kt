package ui.components.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import ui.fixAndroidResUrl

@Composable
@NonRestartableComposable
fun AsyncImage(
    modifier: Modifier = Modifier,
    url: String,
    colorFilter: ColorFilter? = null,
    crossfadeDuration: Int = 240
) = AsyncImage(
    modifier = modifier,
    url = url,
    contentScale = ContentScale.Crop,
    size = 0,
    colorFilter = colorFilter,
    crossfadeDuration = crossfadeDuration
)

@Composable
@NonRestartableComposable
fun AsyncImage(
    modifier: Modifier = Modifier,
    url: String,
    size: Int = 0,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    crossfadeDuration: Int = 240,
) = AsyncImage(
    modifier = modifier,
    url = url,
    size = if (size == 0) Size.ORIGINAL else Size(size, size),
    contentScale = contentScale,
    colorFilter = colorFilter,
    crossfadeDuration = crossfadeDuration
)

@Composable
@NonRestartableComposable
fun AsyncImage(
    modifier: Modifier = Modifier,
    url: String,
    size: Size = Size.ORIGINAL,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    crossfadeDuration: Int = 240
) = AsyncImage(
    model = ImageRequest.Builder(LocalPlatformContext.current)
        .data(fixAndroidResUrl(url))
        .size(size)
        .crossfade(crossfadeDuration)
        .build(),
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
    colorFilter = colorFilter
)