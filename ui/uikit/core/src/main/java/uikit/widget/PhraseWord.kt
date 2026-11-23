package uikit.widget

import android.content.Context
import android.text.SpannableString
import android.util.AttributeSet
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.color.textSecondaryColor
import uikit.R
import uikit.extensions.setColor

class PhraseWord @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyle) {

    private var wordIndex = 0

    init {
        setTextAppearance(R.style.TextAppearance_Body1)
        setTextColor(context.textPrimaryColor)
        setSingleLine()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.viewIdResourceName = "mnemonic.word.$wordIndex"
    }

    fun setData(index: Int, word: String) {
        wordIndex = index - 1
        val prefixIndex = "$index.  "
        text = SpannableString(prefixIndex + word).apply {
            setColor(context.textSecondaryColor, 0, prefixIndex.length)
        }
    }
}