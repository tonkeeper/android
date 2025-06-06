package uikit.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
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
import uikit.extensions.asCircle
import uikit.extensions.getDrawable
import androidx.core.net.toUri
import uikit.extensions.asBitmapDrawable

class FrescoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleDraweeView(context, attrs, defStyle) {

    private var currentUri: Uri? = null

    private val isCurrentLocal: Boolean
        get() = currentUri?.let { UriUtil.isLocalResourceUri(it) } ?: false

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
            super.setImageURI(uriString.toUri(), callerContext)
        }
    }

    override fun setImageURI(uri: Uri, callerContext: Any?) {
        currentUri = uri
        if (callerContext is ResizeOptions) {
            setImageURIWithResize(uri, callerContext)
        } else if (UriUtil.isLocalResourceUri(uri)) {
            loadLocalUri(uri, callerContext)
        } else {
            super.setImageURI(uri, callerContext)
        }
    }

    private fun loadLocalUri(uri: Uri, callerContext: Any?) {
        currentUri = uri
        val drawable = requestDrawable(uri)
        if (drawable is BitmapDrawable) {
            super.setImageURI(uri, callerContext)
        } else if (isCircular) {
            setImageDrawable(drawable.asCircle(), callerContext)
        } else {
            setImageDrawable(drawable, callerContext)
        }
    }

    fun setImageURIWithResize(uri: Uri, resizeOptions: ResizeOptions) {
        currentUri = uri
        val request = ImageRequestBuilder.newBuilderWithSource(uri)
            .setResizeOptions(resizeOptions)
            .build()
        setImageRequest(request)
    }

    override fun setImageRequest(request: ImageRequest) {
        if (UriUtil.isLocalResourceUri(request.sourceUri)) {
            loadLocalUri(request.sourceUri, null)
        } else {
            super.setImageRequest(request)
        }
    }

    fun setPlaceholder(drawable: Drawable?) {
        hierarchy.setPlaceholderImage(drawable)
        //
        // setImageDrawable(null)
        // setPlaceholder(ColorDrawable(Color.TRANSPARENT))
    }

    private fun setImageDrawable(drawable: Drawable, callerContext: Any?) {
        clear(callerContext)
        setPlaceholder(drawable)
        /*if (scaleType == ScaleType.CENTER_INSIDE || drawable is BitmapDrawable) {
            setImageDrawable(drawable)
        } else {
            setPlaceholder(drawable)
        }*/
    }

    private fun requestDrawable(uri: Uri): Drawable {
        return if (uri.pathSegments.isEmpty()) {
            ColorDrawable()
        } else {
            val resourceId = uri.pathSegments[0].toInt()
            getDrawable(resourceId)
        }
    }

    fun clear(callerContext: Any?) {
        try {
            controller = Fresco.newDraweeControllerBuilder()
                .setOldController(controller)
                .setCallerContext(callerContext)
                .setAutoPlayAnimations(true)
                .build()
        } catch (ignored: Throwable) { }
    }
}