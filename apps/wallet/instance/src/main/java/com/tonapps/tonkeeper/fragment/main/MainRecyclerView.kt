package com.tonapps.tonkeeper.fragment.main

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import blur.BlurCompat
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingBottom
import uikit.widget.SimpleRecyclerView

class MainRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleRecyclerView(context, attrs, defStyle) {

    private val paddingVertical = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)
    private val barSize = context.getDimensionPixelSize(uikit.R.dimen.barHeight)

    private var topOffset = 0
    private var bottomOffset = 0

    private val topPadding: Int
        get() = topOffset + barSize

    private val bottomPadding: Int
        get() = bottomOffset + barSize

    private val blurCompat = BlurCompat(context)

    init {
        if (blurCompat.hasBlur) {
            overScrollMode = OVER_SCROLL_NEVER
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val statusInsets = compatInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navigationInsets = compatInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        topOffset = statusInsets.top
        bottomOffset = navigationInsets.bottom
        updatePadding(
            top = paddingVertical + topPadding,
            bottom = paddingVertical + bottomPadding
        )
        applyBlurBounds()
        return super.onApplyWindowInsets(insets)
    }

    override fun draw(canvas: Canvas) {
        blurCompat.draw(canvas) { outputCanvas ->
            super.draw(outputCanvas)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        blurCompat.attached()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blurCompat.detached()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        applyBlurBounds()
    }

    private fun applyBlurBounds() {
        val viewHeight = measuredHeight.toFloat()
        val viewWidth = measuredWidth.toFloat()
        // blurCompat.setBounds(0f, viewHeight - (bottomOffset + barSize), measuredWidth.toFloat(), viewHeight)
        // blurCompat.setSafeArea(0f, topPadding.toFloat(), viewWidth, viewHeight - bottomPadding.toFloat())
    }
}