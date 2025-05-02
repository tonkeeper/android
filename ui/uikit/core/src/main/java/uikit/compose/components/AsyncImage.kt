package uikit.compose.components

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.imagepipeline.common.ResizeOptions
import uikit.widget.FrescoView
import androidx.core.net.toUri

enum class ImageScale {
    CENTER_CROP,
    CENTER_INSIDE
}

enum class ImageShape {
    RECTANGLE,
    ROUNDED_CORNERS,
    CIRCLE
}

@Composable
fun AsyncImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    @DrawableRes placeholderRes: Int? = null,
    @DrawableRes errorRes: Int? = null,
    shape: ImageShape = ImageShape.RECTANGLE,
    cornerRadius: Dp? = null,
    contentScale: ImageScale = ImageScale.CENTER_CROP,
    resizeOptions: ResizeOptions? = null,
    callerContext: Any? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val placeholderDrawable: Drawable? = remember(placeholderRes) {
        placeholderRes?.let { ContextCompat.getDrawable(context, it) }
    }
    val errorDrawable: Drawable? = remember(errorRes) {
        errorRes?.let { ContextCompat.getDrawable(context, it) }
    }

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    AndroidView(
        factory = { ctx ->
            FrescoView(ctx).apply {
                errorDrawable?.let { hierarchy.setFailureImage(it) }
            }
        },
        modifier = modifier.then(semanticsModifier),
        update = { view ->
            when (shape) {
                ImageShape.RECTANGLE -> view.setRound(0f)
                ImageShape.ROUNDED_CORNERS -> {
                    val radiusPx = cornerRadius?.let { with(density) { it.toPx() } } ?: 0f
                    view.setRound(radiusPx)
                }
                ImageShape.CIRCLE -> view.setCircular()
            }

            when (contentScale) {
                ImageScale.CENTER_CROP -> view.setScaleTypeCenterCrop()
                ImageScale.CENTER_INSIDE -> view.setScaleTypeCenterInside()
            }

            view.setPlaceholder(placeholderDrawable)

            val actualCallerContext = resizeOptions ?: callerContext

            when (model) {
                null -> view.clear(actualCallerContext)
                is String -> {
                    try {
                        val uri = model.toUri()
                        if (resizeOptions != null) {
                            view.setImageURIWithResize(uri, resizeOptions)
                        } else {
                            view.setImageURI(uri, actualCallerContext)
                        }
                    } catch (e: Exception) {
                        view.clear(actualCallerContext)
                        errorDrawable?.let { view.hierarchy.setFailureImage(it, ScalingUtils.ScaleType.CENTER_INSIDE) } // Показываем ошибку
                    }
                }
                is Uri -> {
                    if (resizeOptions != null) {
                        view.setImageURIWithResize(model, resizeOptions)
                    } else {
                        view.setImageURI(model, actualCallerContext)
                    }
                }
                is Int -> view.setLocalRes(model)
                else -> {
                    view.clear(actualCallerContext)
                    errorDrawable?.let { view.hierarchy.setFailureImage(it, ScalingUtils.ScaleType.CENTER_INSIDE) }
                }
            }
        }
    )
}
