package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import blur.BlurCompat
import com.tonapps.tonkeeper.extensions.isLightTheme
import com.tonapps.tonkeeper.isBlurDisabled
import com.tonapps.tonkeeperx.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.useAttributes
import uikit.widget.SimpleRecyclerView

class MainRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SimpleRecyclerView(context, attrs, defStyle) {

    private val initialPadding: Int by lazy { paddingTop }
    private val initialPaddingBottom: Int by lazy { paddingBottom }
    private val defaultBarSize = context.getDimensionPixelSize(uikit.R.dimen.barHeight)
    private var topBarSize = defaultBarSize
    private var bottomBarSize = defaultBarSize

    private var topOffset = 0
    private var bottomOffset = 0

    val topPadding: Int
        get() = topOffset + topBarSize

    private val bottomPadding: Int
        get() = bottomOffset + bottomBarSize

    private val blurDisabled = context.isBlurDisabled || context.isLightTheme
    private val topBlur: BlurCompat? = if (!blurDisabled) BlurCompat(context) else null
    private val bottomBlur: BlurCompat? = if (!blurDisabled) BlurCompat(context) else null

    init {
        if (bottomBlur?.hasBlur == true) {
            overScrollMode = OVER_SCROLL_NEVER
        }

        context.useAttributes(attrs, R.styleable.MainRecyclerView) {
            topBarSize = it.getDimensionPixelSize(R.styleable.MainRecyclerView_android_topOffset, defaultBarSize)
            bottomBarSize = it.getDimensionPixelSize(R.styleable.MainRecyclerView_android_bottomOffset, defaultBarSize)
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val statusInsets = compatInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navigationInsets = compatInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        topOffset = statusInsets.top
        bottomOffset = navigationInsets.bottom
        updatePadding(
            top = initialPadding + topPadding,
            bottom = initialPaddingBottom + bottomPadding
        )
        applyBlurBounds()
        return super.onApplyWindowInsets(insets)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (topBlur == null || !topBlur.hasBlur) {
            super.dispatchDraw(canvas)
        } else {
            topBlur.draw(canvas) {
                super.dispatchDraw(it)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (bottomBlur == null || !bottomBlur.hasBlur) {
            super.draw(canvas)
        } else {
            bottomBlur.draw(canvas) {
                super.draw(it)
            }
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        applyBlurBounds()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        topBlur?.attached()
        bottomBlur?.attached()
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        topBlur?.detached()
        bottomBlur?.detached()
    }

    private fun applyBlurBounds() {
        val viewWidth = measuredWidth.toFloat()
        val viewHeight = measuredHeight.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) {
            return
        }
        topBlur?.setBounds(0f, 0f, viewWidth, topPadding.toFloat())
        bottomBlur?.setBounds(0f, viewHeight - bottomPadding, viewWidth, viewHeight)
    }
}