package uikit.widget

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import com.facebook.common.util.UriUtil
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import uikit.extensions.getDrawable


class FrescoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleDraweeView(context, attrs, defStyle) {

    override fun setImageURI(uri: Uri, callerContext: Any?) {
        if (UriUtil.isLocalResourceUri(uri)) {
            setImageDrawable(requestDrawable(uri), callerContext)
        } else {
            hierarchy.setPlaceholderImage(null)
            super.setImageURI(uri, callerContext)
        }
    }

    fun setPlaceholder(drawable: Drawable) {
        hierarchy.setPlaceholderImage(drawable)
    }

    private fun setImageDrawable(drawable: Drawable, callerContext: Any?) {
        clear(callerContext)
        hierarchy.setPlaceholderImage(drawable)
    }

    private fun requestDrawable(uri: Uri): Drawable {
        if (uri.pathSegments.isEmpty()) {
            return ColorDrawable()
        }
        val resourceId = uri.pathSegments[0].toInt()
        return getDrawable(resourceId)
    }

    fun clear(callerContext: Any?) {
        controller = Fresco.newDraweeControllerBuilder()
            .setOldController(controller)
            .setCallerContext(callerContext)
            .setAutoPlayAnimations(true)
            .build()
    }
}