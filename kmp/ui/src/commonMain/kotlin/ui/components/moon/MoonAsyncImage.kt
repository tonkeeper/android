package ui.components.moon

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import ui.fixAndroidResUrl

@Composable
@NonRestartableComposable
fun MoonAsyncImage(
    image: Any?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    builder: ImageRequest.Builder.() -> ImageRequest.Builder = { this },
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    size: Dp = Dp.Unspecified,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    crossfadeDuration: Int = 240
) {
    val context = LocalPlatformContext.current
    val res = if (image is String) fixAndroidResUrl(image) else image
    val request = remember(context, image, builder) {
        ImageRequest.Builder(context)
            .data(res)
            .crossfade(crossfadeDuration)
            .run { builder() }
            .run {
                if (size == Dp.Unspecified) {
                    size(Size.ORIGINAL)
                } else {
                    this
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.run { if (size != Dp.Unspecified) size(size, size) else this },
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
}

