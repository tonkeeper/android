package uikit.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import com.facebook.drawee.view.SimpleDraweeView

class FrescoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleDraweeView(context, attrs, defStyle) {

    override fun setImageURI(uri: Uri?, callerContext: Any?) {
        if (uri?.scheme == "drawable") {
            var source = uri.host
            if (source.isNullOrEmpty()) {
                source = uri.path?.substring(1)
            }
            val resId = source?.toInt() ?: 0
            super.setImageURI("")
            super.setImageResource(resId)
        } else {
            super.setImageURI(uri, callerContext)
        }
    }

}