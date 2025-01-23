package uikit.widget

import android.content.Context
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.color.textSecondaryColor
import uikit.R
import uikit.extensions.dp
import uikit.extensions.setColor
import uikit.extensions.withGreenBadge

class PhraseWord @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyle) {

    init {
        setTextAppearance(R.style.TextAppearance_Body1)
        setTextColor(context.textPrimaryColor)
        setSingleLine()
    }

    fun setData(index: Int, word: String) {
        val prefixIndex = "$index.  "
        text = SpannableString(prefixIndex + word).apply {
            setColor(context.textSecondaryColor, 0, prefixIndex.length)
        }
    }
}