package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.extensions.dp
import uikit.extensions.setPaddingEnd
import uikit.extensions.setPaddingVertical

class PhraseWords @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val isSmallScreen: Boolean by lazy {
        1320 >= context.resources.displayMetrics.heightPixels
    }

    private val isVerySmallScreen: Boolean by lazy {
        720 >= context.resources.displayMetrics.heightPixels
    }

    init {
        orientation = HORIZONTAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        pivotY = 0f
        val screenHeight = context.resources.displayMetrics.heightPixels

        val isSmall = 1320 >= context.resources.displayMetrics.heightPixels
        val isVerySmall = 720 >= context.resources.displayMetrics.heightPixels
        if (isVerySmall) {
            // scale = .4f
            // translationX = (measuredWidth * (1 - scale) / 2)
        } else if (isSmall) {
            // scale = .7f
            // translationX = (measuredWidth * (1 - scale) / 2)
        } else {
            // scale = 1f
            // translationX = 0f
        }
    }

    fun setWords(words: Array<String>) {
        setWords(words.toList())
    }

    fun setWords(words: List<String>) {
        removeAllViews()

        val phraseLayoutParams = LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        var row = insertWordRow()

        for ((index, word) in words.withIndex()) {
            if (index == words.size / 2) {
                row.setPaddingEnd(72.dp)
                row = insertWordRow()
            }
            val wordView = PhraseWord(context)
            wordView.setData(index + 1, word)
            if (isSmallScreen) {
                wordView.setPaddingVertical(2.dp)
            } else if (!isVerySmallScreen) {
                wordView.setPaddingVertical(4.dp)
            }
            row.addView(wordView, phraseLayoutParams)
        }
    }

    private fun insertWordRow(): LinearLayoutCompat {
        val row = LinearLayoutCompat(context)
        row.orientation = VERTICAL
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f))
        return row
    }

}