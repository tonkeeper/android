package uikit.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.hapticConfirm

class ToastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle), ValueAnimator.AnimatorUpdateListener,
    Animator.AnimatorListener {

    private data class Data(
        val loading: Boolean,
        val text: CharSequence,
        val color: Int
    )

    private var statusBarHeight: Int = 0
    private var currentData: Data? = null
        set(value) {
            field = value
            runData()
        }

    private val dataQueue = mutableListOf<Data>()
    private val loaderView: View
    private val textView: AppCompatTextView

    private val animator: ValueAnimator by lazy {
        val startY = -height.toFloat()
        val endY = statusBarHeight + 24f.dp

        val valueAnimator = ValueAnimator.ofFloat(startY, endY)
        valueAnimator.duration = 200L
        valueAnimator.addListener(this@ToastView)
        valueAnimator.addUpdateListener(this@ToastView)
        valueAnimator
    }

    private val horizontalOffset = 24.dp
    private val verticalOffset = context.getDimensionPixelSize(R.dimen.offsetMedium)

    init {
        inflate(context, R.layout.view_toast, this)
        setBackgroundResource(R.drawable.bg_content_tint_24)
        setPadding(horizontalOffset, verticalOffset, horizontalOffset, verticalOffset)
        visibility = View.GONE
        loaderView = findViewById(R.id.toast_loader)
        textView = findViewById(R.id.toast_text)
    }

    fun show(text: CharSequence, loading: Boolean, color: Int = context.backgroundContentTintColor) {
        val data = createData(loading, text, color)
        run(data)
    }

    private fun run(data: Data) {
        val cancelCurrent = currentData != null && currentData?.text == data.text && currentData?.color == data.color
        postOnAnimation {
            if (cancelCurrent) {
                hide()
            } else if (currentData == null) {
                currentData = data
            } else {
                dataQueue.add(data)
            }
        }
    }

    private fun createData(loading: Boolean, text: CharSequence, color: Int): Data {
        return Data(
            loading = loading,
            text = text,
            color = if (color == Color.TRANSPARENT) {
                context.backgroundContentTintColor
            } else {
                color
            }
        )
    }

    private fun nextQueue() {
        currentData = dataQueue.removeFirstOrNull()
    }

    private fun runData() {
        val data = currentData ?: return
        hapticConfirm()
        textView.text = data.text
        background.setTint(data.color)
        visibility = View.VISIBLE

        doOnLayout {
            if (data.loading) {
                showLoading()
            } else if (loaderView.visibility == View.VISIBLE) {
                hide()
            } else {
                showDefault()
            }
        }
    }

    private fun showLoading() {
        loaderView.visibility = View.VISIBLE
        show()
    }

    private fun showDefault() {
        loaderView.visibility = View.GONE
        show()
        postDelayed(::hide, 2600)
    }

    private fun show() {
        animator.start()
    }

    private fun hide() {
        animator.reverse()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        translationY = animation.animatedValue as Float
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        statusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top
        return super.onApplyWindowInsets(insets)
    }

    override fun onAnimationStart(animation: Animator) {
        visibility = View.VISIBLE
    }

    override fun onAnimationEnd(animation: Animator) {
        if (translationY <= -height.toFloat() + 1) {
            loaderView.visibility = View.GONE
            visibility = View.GONE
            nextQueue()
        }
    }

    override fun onAnimationCancel(animation: Animator) {

    }

    override fun onAnimationRepeat(animation: Animator) {

    }
}