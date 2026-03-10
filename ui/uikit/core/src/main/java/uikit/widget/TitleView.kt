package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.uikit.color.textPrimaryColor
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingVertical

class TitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyle) {

    init {
        setTextAppearance(R.style.TextAppearance_H3)
        setTextColor(context.textPrimaryColor)
        setPaddingVertical(context.getDimensionPixelSize(R.dimen.offsetMedium))
    }
}