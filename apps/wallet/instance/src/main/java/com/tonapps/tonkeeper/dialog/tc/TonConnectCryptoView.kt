package com.tonapps.tonkeeper.dialog.tc

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.res.ResourcesCompat
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.textTertiaryColor
import uikit.extensions.dp
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.sp

class TonConnectCryptoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val keyView: AnimationTextView
    private val cryptView: AnimationTextView
    private val backgroundColor = context.backgroundPageColor

    init {
        inflate(context, R.layout.view_ton_connect_crypto, this)
        orientation = HORIZONTAL
        setPaddingHorizontal(4.dp)

        keyView = findViewById(R.id.key)
        keyView.foreground = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(backgroundColor, Color.TRANSPARENT)
        )

        cryptView = findViewById(R.id.crypt)
        cryptView.text = "* ".repeat(50)
        cryptView.foreground = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(backgroundColor, Color.TRANSPARENT)
        )
    }

    fun setKey(key: String) {
        keyView.text = key
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(32.dp, MeasureSpec.EXACTLY))
    }

    override fun hasOverlappingRendering() = false

    class AnimationTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = android.R.attr.textViewStyle,
    ) : View(context, attrs, defStyle), ValueAnimator.AnimatorUpdateListener {

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = null
        }

        private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f.sp
            typeface = ResourcesCompat.getFont(context, uikit.R.font.montserrat_medium)
            color = context.textTertiaryColor
        }

        private var measureText = 0f

        var text: String = ""
            set(value) {
                if (field != value) {
                    field = value
                    measureText = paint.measureText(text)
                    invalidate()
                }
            }

        private var offsetY = 0f
        private var offsetX = 0f

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.addUpdateListener(this)
            animator.start()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.removeUpdateListener(this)
            animator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawText(text, offsetX, offsetY, paint)
            canvas.drawText(text, offsetX - measureText, offsetY, paint)
        }

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val progress = animation.animatedValue as Float
            offsetX = progress * measureText
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            offsetY = (measuredHeight - paint.descent() - paint.ascent()) / 2
        }

        override fun hasOverlappingRendering() = false
    }
}