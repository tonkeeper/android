package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.extensions.dp

class PhraseWord @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val indexView = AppCompatTextView(context).apply {
        setTextAppearance(R.style.TextAppearance_Body1)
        setTextColor(context.getColor(R.color.textSecondary))
        setSingleLine()
    }

    private val wordView = AppCompatTextView(context).apply {
        setTextAppearance(R.style.TextAppearance_Body1)
        setTextColor(context.getColor(R.color.textPrimary))
        setSingleLine()
    }

    init {
        orientation = HORIZONTAL
        addView(indexView, LayoutParams(24.dp, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        })
        addView(wordView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.BOTTOM
        })
    }

    fun setData(index: Int, word: String) {
        indexView.text = "$index."
        wordView.text = word
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(32.dp, MeasureSpec.EXACTLY))
    }
}