package ui.components.moon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import ui.fixAndroidResUrl

@Composable
fun MoonAsyncImage(
    url: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    builder: ImageRequest.Builder.() -> ImageRequest.Builder = { this },
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    size: Size = Size.ORIGINAL,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    crossfadeDuration: Int = 240
) = AsyncImage(
    model = ImageRequest.Builder(LocalPlatformContext.current)
        .data(if (url is String) fixAndroidResUrl(url) else url)
        .crossfade(crossfadeDuration)
        .run { builder() }
        .size(size)
        .build(),
    contentDescription = contentDescription,
    modifier = modifier,
    placeholder = placeholder,
    error = error,
    fallback = fallback,
    onLoading = onLoading,
    onSuccess = onSuccess,
    onError = onError,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    filterQuality = filterQuality,
)

