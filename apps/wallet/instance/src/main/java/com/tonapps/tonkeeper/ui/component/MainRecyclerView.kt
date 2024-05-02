package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import blur.BlurCompat
import org.koin.core.time.measureDuration
import uikit.extensions.getDimensionPixelSize
import uikit.widget.SimpleRecyclerView

class MainRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleRecyclerView(context, attrs, defStyle) {

    private val bottomBlur = BlurCompat(context)

    private val paddingVertical = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)
    private val barSize = context.getDimensionPixelSize(uikit.R.dimen.barHeight)

    private var topOffset = 0
    private var bottomOffset = 0

    private val topPadding: Int
        get() = topOffset + barSize

    private val bottomPadding: Int
        get() = bottomOffset + barSize

    init {
        if (bottomBlur.hasBlur) {
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
        bottomBlur.draw(canvas) {
            super.draw(it)
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        applyBlurBounds()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bottomBlur.attached()
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bottomBlur.detached()
    }

    private fun applyBlurBounds() {
        val viewWidth = measuredWidth.toFloat()
        val viewHeight = measuredHeight.toFloat()
        // header blur = 0f, 0f, viewWidth, topPadding.toFloat()
        // bottom blur = 0f, viewHeight - bottomPadding, viewWidth, viewHeight
        bottomBlur.setBounds(0f, viewHeight - bottomPadding, viewWidth, viewHeight)
    }
}