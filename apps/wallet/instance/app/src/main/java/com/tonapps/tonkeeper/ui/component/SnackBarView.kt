package com.tonapps.tonkeeper.ui.component

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
    private val textView: AppCompatTextView
    private val button: AppCompatTextView
    private var hideRunnable: Runnable? = null

    init {
        inflate(context, R.layout.view_snack_bar, this)
        setPadding(offsetMedium)
        textView = findViewById(R.id.text)
        button = findViewById(R.id.button)
        setBackgroundResource(uikit.R.drawable.bg_content)
    }

    private fun clearPendingHide() {
        hideRunnable?.let { removeCallbacks(it) }
        hideRunnable = null
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

    private fun removeSelf() {
        clearPendingHide()
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    fun show() {
        doOnLayout {
            translationY = -measuredHeight.toFloat()
            startShowAnimation()
        }
    }

    private fun startShowAnimation() {
        clearPendingHide()
        animate().cancel()

        val targetY = (statusBarHeight + offsetMedium).toFloat()

        animate()
            .translationY(targetY)
            .setDuration(300)
            .withEndAction(::hideDelayed)
            .start()
    }

    private fun startHideAnimation() {
        clearPendingHide()
        animate().cancel()

        animate()
            .translationY(-measuredHeight.toFloat())
            .setDuration(220)
            .withEndAction(::removeSelf)
            .start()
    }

    private fun hideDelayed() {
        postDelayed({
            startHideAnimation()
        }, 3000)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearPendingHide()
        animate().cancel()
    }

    companion object {

        fun show(context: Context, text: String, onClick: () -> Unit) {
            show(context, text, OnClickListener { onClick() })
        }

        fun show(context: Context, text: String, onClickListener: OnClickListener) {
            val baseView = BaseWalletActivity.findBaseView(context) ?: return

            val view = SnackBarView(context)
            view.setText(text)
            view.setButtonOnClickListener(onClickListener)
            view.show()

            baseView.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = view.offsetMedium
                rightMargin = view.offsetMedium
            })
        }
    }
}