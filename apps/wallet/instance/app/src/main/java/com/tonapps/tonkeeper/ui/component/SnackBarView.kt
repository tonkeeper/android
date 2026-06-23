package com.tonapps.tonkeeper.ui.component

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import com.tonapps.tonkeeper.ui.base.BaseWalletActivity
import com.tonapps.tonkeeperx.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.statusBarHeight
import uikit.widget.RowLayout

class SnackBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val offsetMedium = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)
    private val size = context.getDimensionPixelSize(uikit.R.dimen.itemHeight)
    private val textView: AppCompatTextView
    private val button: AppCompatTextView

    init {
        inflate(context, R.layout.view_snack_bar, this)
        setPadding(offsetMedium)
        textView = findViewById(R.id.text)
        button = findViewById(R.id.button)
        setBackgroundResource(uikit.R.drawable.bg_content)
    }

    fun setButtonOnClickListener(onClickListener: OnClickListener) {
        button.setOnClickListener {
            startHideAnimation()
            onClickListener.onClick(it)
        }
    }

    fun setText(text: String) {
        textView.text = text
    }

    private fun hide() {
        (parent as? ViewGroup)?.let {
            removeView(this)
        }
    }

    private fun processForTests(durationMs: Long): Long {
        val intent = (context as? Activity)?.intent
        val isMaestro = intent?.getStringExtra("isMaestro") == "true"
        return durationMs + if (isMaestro) 3000L else 0L
    }

    fun show(durationMs: Long) {
        doOnLayout {
            translationY = hiddenTranslationY()
            startShowAnimation(processForTests(durationMs))
        }
    }

    private fun startShowAnimation(durationMs: Long) {
        animate().translationY((statusBarHeight + offsetMedium).toFloat()).setDuration(300).withEndAction {
            hideDelayed(durationMs)
        }
    }

    private fun startHideAnimation() {
        animate().translationY(hiddenTranslationY()).setDuration(220).withEndAction {
            hide()
        }
    }

    private fun hideDelayed(delayMs: Long) {
        if (delayMs <= 0) {
            return
        }
        postDelayed({
            startHideAnimation()
        }, delayMs)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, maxOf(measuredHeight, size))
    }

    private fun hiddenTranslationY(): Float {
        return -maxOf(size, measuredHeight, height).toFloat()
    }

    companion object {

        private const val DEFAULT_SHOWING_DURATION_MS = 3000L

        fun show(
            context: Context,
            text: String,
            buttonText: CharSequence,
            durationMs: Long = DEFAULT_SHOWING_DURATION_MS,
            onClickListener: OnClickListener,
        ) {
            val baseView = BaseWalletActivity.findBaseView(context) ?: return

            val view = SnackBarView(context)
            view.setText(text)
            view.button.text = buttonText
            view.setButtonOnClickListener(onClickListener)
            view.show(durationMs)

            baseView.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = view.offsetMedium
                rightMargin = view.offsetMedium
            })
        }
    }
}
