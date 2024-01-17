package com.tonkeeper.fragment.main

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import blur.BlurCompat
import com.tonkeeper.App
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingBottom

class MainRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private val barSize = context.getDimensionPixelSize(uikit.R.dimen.barHeight)
    private var bottomOffset = 0
    private val tabsHeight: Int
        get() = barSize + bottomOffset
    private val bottomPadding: Int
        get() = tabsHeight + context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)

    private val blurCompat = BlurCompat(context, App.settings.experimental.hasBlur31, App.settings.experimental.hasBlurLegacy)

    init {
        if (blurCompat.hasBlur) {
            overScrollMode = OVER_SCROLL_NEVER
        }
        setPaddingBottom(bottomPadding)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val navigationInsets = compatInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        bottomOffset = navigationInsets.bottom
        setPaddingBottom(bottomPadding)
        applyBlurBounds()
        return super.onApplyWindowInsets(insets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        blurCompat.attached()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blurCompat.detached()
    }

    override fun draw(canvas: Canvas) {
        blurCompat.draw(canvas) { outputCanvas ->
            super.draw(outputCanvas)
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        applyBlurBounds()
    }

    private fun applyBlurBounds() {
        val viewHeight = measuredHeight.toFloat()
        blurCompat.setBounds(0f, viewHeight - tabsHeight, measuredWidth.toFloat(), viewHeight)
    }
}