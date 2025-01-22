package uikit.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
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
        pivotY = 0f
        val isSmall = 1320 >= context.resources.displayMetrics.heightPixels
        val isVerySmall = 720 >= context.resources.displayMetrics.heightPixels
        if (isVerySmall) {
            scale = .4f
            // translationX = (measuredWidth * (1 - scale) / 2) + 32.dp
        } else if (isSmall) {
            scale = .7f
            // translationX = (measuredWidth * (1 - scale) / 2) + 20.dp
        } else {
            scale = 1f
            translationX = 0f
        }
    }

    fun setWords(words: Array<String>) {
        setWords(words.toList())
    }

    fun setWords(words: List<String>) {
        removeAllViews()

        val layoutParams = LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        var row = insertWordRow()

        addView(View(context), LayoutParams(72.dp, LayoutParams.WRAP_CONTENT))

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
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.CENTER
        })
        return row
    }

}