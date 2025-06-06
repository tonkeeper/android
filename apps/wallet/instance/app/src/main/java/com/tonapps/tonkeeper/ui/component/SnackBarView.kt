package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import androidx.core.view.setMargins
import androidx.core.view.setPadding
import com.tonapps.tonkeeper.ui.base.BaseWalletActivity
import com.tonapps.tonkeeperx.R
import uikit.extensions.dp
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

    fun show() {
        translationY = -size.toFloat()
        doOnLayout {
            startShowAnimation()
        }
    }

    private fun startShowAnimation() {
        animate().translationY((statusBarHeight + offsetMedium).toFloat()).setDuration(300).withEndAction {
            hideDelayed()
        }
    }

    private fun startHideAnimation() {
        animate().translationY(-size.toFloat()).setDuration(220).withEndAction {
            hide()
        }
    }

    private fun hideDelayed() {
        postDelayed({
            startHideAnimation()
        }, 3000)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }

    companion object {

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