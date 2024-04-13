package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import uikit.R
import uikit.extensions.dp

class NumPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ViewGroup(context, attrs, defStyle), View.OnClickListener {

    private companion object {
        const val COLUMN_COUNT = 3
        const val ROW_COUNT = 4

        const val BUTTON_COUNT = COLUMN_COUNT * ROW_COUNT

        val BUTTON_HEIGHT = 72.dp
        val HEIGHT = BUTTON_HEIGHT * ROW_COUNT
    }

    private val backspaceView = inflate(context, R.layout.view_numpad_delete, null)

    var doOnNumberClick: ((number: Int) -> Unit)? = null
    var doOnBackspaceClick: (() -> Unit)? = null
        set(value) {
            field = value
            backspaceView.setOnClickListener {
                value?.invoke()
            }
        }

    var backspace: Boolean = false
        set(value) {
            field = value
            backspaceView.alpha = if (value) 1f else 0f
        }

    init {
        for (i in 0 until BUTTON_COUNT) {
            val button: View = createButton(i)
            button.setOnClickListener(this)
            addView(button)
        }
    }

    private fun createButton(index: Int): View {
        return if (index == 9) {
            View(context)
        } else if (index == 10) {
            createNumberButton(0)
        } else if (index < 9) {
            createNumberButton(index + 1)
        } else {
            backspaceView
        }
    }

    private fun createNumberButton(number: Int): View {
        val view = inflate(context, R.layout.view_numpad_button, null) as AppCompatTextView
        view.text = number.toString()
        view.tag = number
        return view
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (i in 0 until BUTTON_COUNT) {
            getChildAt(i).isEnabled = enabled
        }
    }

    override fun onClick(v: View) {
        val number = v.tag as? Int ?: return

        doOnNumberClick?.invoke(number)
        v.playSoundEffect(SoundEffectConstants.CLICK)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val buttonWidth = measuredWidth / COLUMN_COUNT
        for (i in 0 until childCount) {
            val row = i / COLUMN_COUNT
            val column = i % COLUMN_COUNT
            val top = row * BUTTON_HEIGHT
            val left = column * buttonWidth
            val button = getChildAt(i)
            button.layout(left, top, left + buttonWidth, top + BUTTON_HEIGHT)
            button.measure(
                MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(BUTTON_HEIGHT, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(HEIGHT, MeasureSpec.EXACTLY))
    }
}