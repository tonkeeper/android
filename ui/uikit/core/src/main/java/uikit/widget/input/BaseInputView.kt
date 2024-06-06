package uikit.widget.input

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.graphics.withSave
import uikit.drawable.InputDrawable
import uikit.extensions.dp
import uikit.extensions.getDimension
import uikit.extensions.range

abstract class BaseInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private companion object {
        private val collapsedContentOffsetY = 6f.dp
    }

    private val offsetMedium = context.getDimension(uikit.R.dimen.offsetMedium)
    private val inputDrawable = InputDrawable(context)
    private val hintDrawable = HintDrawable(context)

    private val hintAnimationCallback = ValueAnimator.AnimatorUpdateListener { animation ->
        val progress = animation.animatedValue as Float
        hintDrawable.setProgress(progress)
        getContentView().translationY = progress.range(collapsedContentOffsetY, 0f)
        invalidate()
    }

    private val hintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120
        addUpdateListener(hintAnimationCallback)
    }

    var expanded: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    hintAnimator.start()
                } else {
                    hintAnimator.reverse()
                }
            }
        }

    var active: Boolean
        get() = inputDrawable.active
        set(value) {
            inputDrawable.active = value
        }

    init {
        background = inputDrawable
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withSave {
            hintDrawable.draw(canvas)
        }
    }

    abstract fun getContentView(): View

    fun setHint(@StringRes resId: Int) {
        setHint(resources.getString(resId))
    }

    fun setHint(text: CharSequence) {
        hintDrawable.text = text
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        hintDrawable.bounds = Rect(offsetMedium.toInt(), 0, w, h)
    }
}