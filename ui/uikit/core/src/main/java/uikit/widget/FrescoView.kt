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
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.request.ImageRequest
import uikit.extensions.getDrawable

class FrescoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleDraweeView(context, attrs, defStyle) {

    fun setRound(radius: Float) {
        hierarchy.roundingParams = RoundingParams.fromCornersRadius(radius)
    }

    fun setCircular() {
        hierarchy.roundingParams = RoundingParams.asCircle()
    }

    fun setLocalRes(resId: Int) {
        setImageURI(UriUtil.getUriForResourceId(resId))
    }

    override fun setImageURI(uri: Uri, callerContext: Any?) {
        if (UriUtil.isLocalResourceUri(uri)) {
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

    override fun setImageRequest(request: ImageRequest) {
        setImageDrawable(null)
        setPlaceholder(ColorDrawable(Color.TRANSPARENT))
        super.setImageRequest(request)
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
        if (drawable is VectorDrawable || drawable is ColorDrawable) {
            return drawable
        }
        return null
    }

    fun clear(callerContext: Any?) {
        controller = Fresco.newDraweeControllerBuilder()
            .setOldController(controller)
            .setCallerContext(callerContext)
            .setAutoPlayAnimations(true)
            .build()
    }
}