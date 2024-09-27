package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.extensions.dp
import uikit.extensions.scale

class PhraseWords @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    init {
        orientation = HORIZONTAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val isSmall = 1280 >= context.resources.displayMetrics.heightPixels
        scale = if (isSmall) {
            0.8f
        } else {
            1f
        }

        /*if (layoutHeight > screenHeight) {
            scale = screenHeight / layoutHeight.toFloat()
            pivotY = 0f
            translationX = measuredWidth * (1 - scale) / 2
        }*/
    }

    fun setWords(words: Array<String>) {
        setWords(words.toList())
    }

    fun setWords(words: List<String>) {
        removeAllViews()

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        var row = insertWordRow()

        addView(View(context), LayoutParams(72.dp, LayoutParams.MATCH_PARENT))

        for ((index, word) in words.withIndex()) {
            if (index == words.size / 2) {
                row = insertWordRow()
            }
            val wordView = PhraseWord(context)
            wordView.setData(index + 1, word)
            row.addView(wordView, layoutParams)
        }
    }

    private fun insertWordRow(): LinearLayoutCompat {
        val row = LinearLayoutCompat(context)
        row.orientation = VERTICAL
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f))
        return row
    }

}