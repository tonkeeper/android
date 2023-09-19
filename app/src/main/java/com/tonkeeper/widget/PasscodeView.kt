package com.tonkeeper.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.dp
import com.tonkeeper.uikit.extensions.scale
import com.tonkeeper.uikit.interpolator.ReverseInterpolator

class PasscodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val count = 4
    private val defaultColor = context.getColor(R.color.fieldBackground)
    private val activeColor = context.getColor(R.color.fieldActiveBorder)
    private val errorColor = context.getColor(R.color.fieldErrorBorder)
    private var doneColor = context.getColor(R.color.accentGreen)

    private val defaultSize = 12.dp
    private val activeSize = 16.dp
    private val activeScale = activeSize.toFloat() / defaultSize.toFloat()
    private val margin = 8.dp

    private var lastActive = 0

    init {
        orientation = HORIZONTAL
        for (i in 0 until count) {
            val view = View(context)
            view.background = createOvalDrawable(defaultColor)
            view.layoutParams = LayoutParams(defaultSize, defaultSize).apply {
                marginStart = margin
                marginEnd = margin
                gravity = Gravity.CENTER
            }
            addView(view)
        }
    }

    fun addActive() {
        if (lastActive >= count) {
            setError()
            return
        }

        val view = getChildAt(lastActive)
        view.background = createOvalDrawable(activeColor)
        startScaleAnimation(view)

        lastActive++

        if (lastActive >= count) {
            setError()
            return
        }
    }

    fun setError() {
        for (i in 0 until count) {
            val view = getChildAt(i)
            view.background = createOvalDrawable(errorColor)
            startScaleAnimation(view)
        }
    }

    private fun createOvalDrawable(color: Int): Drawable {
        return ShapeDrawable(OvalShape()).apply {
            paint.color = color
        }
    }

    private fun startScaleAnimation(view: View) {
        view.animate().scale(activeScale).setDuration(320).setInterpolator(ReverseInterpolator()).start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(activeSize, MeasureSpec.EXACTLY))
    }

}