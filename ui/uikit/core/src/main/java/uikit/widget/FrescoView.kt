package uikit.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.util.AttributeSet
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.facebook.common.util.UriUtil
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.drawable.RoundedCornersDrawable
import com.facebook.drawee.drawable.RoundedDrawable
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import uikit.extensions.getDrawable

class FrescoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleDraweeView(context, attrs, defStyle) {

    val isCircular: Boolean
        get() = hierarchy.roundingParams?.roundAsCircle ?: false

    fun setRound(radius: Float) {
        hierarchy.roundingParams = RoundingParams.fromCornersRadius(radius)
    }

    fun setCircular() {
        hierarchy.roundingParams = RoundingParams.asCircle()
    }

    fun setScaleTypeCenterInside() {
        hierarchy.actualImageScaleType = ScalingUtils.ScaleType.CENTER_INSIDE
    }

    fun setScaleTypeCenterCrop() {
        hierarchy.actualImageScaleType = ScalingUtils.ScaleType.CENTER_CROP
    }

    fun setLocalRes(resId: Int) {
        setImageURI(UriUtil.getUriForResourceId(resId))
    }

    override fun setImageURI(uriString: String?, callerContext: Any?) {
        if (uriString == null) {
            clear(callerContext)
        } else {
            super.setImageURI(Uri.parse(uriString), callerContext)
        }
    }

    override fun setImageURI(uri: Uri, callerContext: Any?) {
        if (callerContext is ResizeOptions) {
            setImageURIWithResize(uri, callerContext)
        } else if (UriUtil.isLocalResourceUri(uri)) {
            loadLocalUri(uri, callerContext)
        } else {
            hierarchy.setPlaceholderImage(null)
            super.setImageURI(uri, callerContext)
        }
    }

    private fun loadLocalUri(uri: Uri, callerContext: Any?) {
        val drawable = requestDrawable(uri)
        if (drawable == null) {
            hierarchy.setPlaceholderImage(null)
            super.setImageURI(uri, callerContext)
        } else {
            setImageDrawable(drawable, callerContext)
        }
    }

    private fun setImageURIWithResize(uri: Uri, resizeOptions: ResizeOptions) {
        val request = ImageRequestBuilder.newBuilderWithSource(uri)
            .setResizeOptions(resizeOptions)
            .build()
        setImageRequest(request)
    }

    override fun setImageRequest(request: ImageRequest) {
        if (UriUtil.isLocalResourceUri(request.sourceUri)) {
            loadLocalUri(request.sourceUri, null)
        } else {
            setImageDrawable(null)
            setPlaceholder(ColorDrawable(Color.TRANSPARENT))
            super.setImageRequest(request)
        }
    }

    fun setPlaceholder(drawable: Drawable) {
        hierarchy.setPlaceholderImage(drawable)
    }

    private fun setImageDrawable(drawable: Drawable, callerContext: Any?) {
        clear(callerContext)
        if (scaleType == ScaleType.CENTER_INSIDE) {
            setImageDrawable(drawable)
        } else {
            setPlaceholder(drawable)
        }
    }

    private fun requestDrawable(uri: Uri): Drawable? {
        val drawable = if (uri.pathSegments.isEmpty()) {
            ColorDrawable()
        } else {
            val resourceId = uri.pathSegments[0].toInt()
            getDrawable(resourceId)
        }
        val iconDrawable = if (drawable is VectorDrawable || drawable is ColorDrawable) {
            drawable
        } else return null

        if (isCircular) {
            return RoundedCornersDrawable(iconDrawable).apply {
                setType(RoundedCornersDrawable.Type.CLIPPING)
            }
        }
        return iconDrawable
    }

    fun clear(callerContext: Any?) {
        controller = Fresco.newDraweeControllerBuilder()
            .setOldController(controller)
            .setCallerContext(callerContext)
            .setAutoPlayAnimations(true)
            .build()
    }
}