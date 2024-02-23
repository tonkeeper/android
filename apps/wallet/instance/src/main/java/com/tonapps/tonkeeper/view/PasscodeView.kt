package com.tonapps.tonkeeper.view

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.fieldActiveBorderColor
import com.tonapps.uikit.color.fieldBackgroundColor
import com.tonapps.uikit.color.fieldErrorBorderColor
import uikit.extensions.dp
import uikit.extensions.hapticConfirm
import uikit.extensions.hapticReject
import uikit.extensions.scale
import uikit.interpolator.ReverseInterpolator

class PasscodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val count = 4

    private val margin = 8.dp

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        orientation = HORIZONTAL
        for (i in 0 until count) {
            val view = DotView(context)
            view.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = margin
                marginEnd = margin
                gravity = Gravity.CENTER
            }
            addView(view)
        }
    }

    fun setCount(fullCount: Int) {
        if (fullCount == 0) {
            setDefault()
            return
        }

        for (i in 0 until count) {
            val view = getDotView(i)
            if (fullCount > i && view.state != DotView.State.ERROR) {
                view.state = DotView.State.ACTIVE
            } else if (fullCount < i) {
                view.state = DotView.State.DEFAULT
            }
        }
    }


    fun setError() {
        setState(DotView.State.ERROR)
        hapticReject()
    }

    fun setSuccess() {
        setState(DotView.State.DONE)
        hapticConfirm()
    }

    fun setDefault() {
        setState(DotView.State.DEFAULT)
    }

    private fun setState(state: DotView.State) {
        for (i in 0 until count) {
            getDotView(i).state = state
        }
    }

    private fun getDotView(index: Int): DotView {
        return getChildAt(index) as DotView
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(16.dp, MeasureSpec.EXACTLY))
    }

    private class DotView(context: Context): View(context) {

        enum class State {
            DEFAULT,
            ACTIVE,
            ERROR,
            DONE
        }

        private val defaultColor = context.fieldBackgroundColor
        private val activeColor = context.fieldActiveBorderColor
        private val errorColor = context.fieldErrorBorderColor
        private var doneColor = context.accentGreenColor

        private val defaultSize = 12.dp
        private val activeSize = 16.dp
        private val activeScale = activeSize.toFloat() / defaultSize.toFloat()

        private val ovalDrawable = ShapeDrawable(OvalShape())

        var state: State = State.DEFAULT
            set(value) {
                if (field != value) {
                    field = value
                    when (value) {
                        State.DEFAULT -> setDefault()
                        State.ACTIVE -> setActive()
                        State.ERROR -> setError()
                        State.DONE -> setDone()
                    }
                }
            }

        init {
            background = ovalDrawable
            setDefault()
        }

        private fun setColor(color: Int) {
            ovalDrawable.paint.color = color
            invalidate()
        }

        private fun setDefault() {
            setColor(defaultColor)
            startScaleAnimation()
        }

        private fun setActive() {
            setColor(activeColor)
            startScaleAnimation()
        }

        private fun setError() {
            setColor(errorColor)
            startScaleAnimation()
        }

        private fun setDone() {
            setColor(doneColor)
            startScaleAnimation()
        }

        private fun startScaleAnimation() {
            if (!isAttachedToWindow) {
                return
            }

            val duration = if (state == State.DONE) {
                320L
            } else 180L

            animate().scale(activeScale)
                .setDuration(duration)
                .setInterpolator(ReverseInterpolator())
                .start()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.makeMeasureSpec(defaultSize, MeasureSpec.EXACTLY)
            super.onMeasure(size, size)
        }


    }

}