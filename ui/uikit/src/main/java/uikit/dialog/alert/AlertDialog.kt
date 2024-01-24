package uikit.dialog.alert

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import uikit.R
import uikit.extensions.dp
import uikit.extensions.scale
import uikit.extensions.setTextOrGone

class AlertDialog private constructor(
    context: Context,
    params: Params
): Dialog(context, R.style.Widget_AlertDialog_Custom), ValueAnimator.AnimatorUpdateListener {

    private companion object {
        private const val initScale = 0.86f
        private val initTranslationY = 16f.dp
        private val showInterpolator = OvershootInterpolator(1.3f)
        private val hideInterpolator = AccelerateDecelerateInterpolator()
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = showInterpolator
        doOnStart { bodyView.setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
        doOnEnd { bodyView.setLayerType(View.LAYER_TYPE_NONE, null) }
        addUpdateListener(this@AlertDialog)
    }

    private val rootView: View
    private val outsideView: View
    private val bodyView: View
    private val titleView: AppCompatTextView
    private val messageView: AppCompatTextView
    private val negativeButton: AppCompatTextView
    private val positiveButton: AppCompatTextView

    init {
        setContentView(R.layout.dialog_alert)

        rootView = findViewById(R.id.alert_root)

        outsideView = findViewById(R.id.alert_outside)
        outsideView.setOnClickListener { dismiss() }

        bodyView = findViewById(R.id.alert_body)
        bodyView.setOnClickListener {  }

        titleView = findViewById(R.id.alert_title)
        titleView.setTextOrGone(params.title)

        messageView = findViewById(R.id.alert_message)
        messageView.setTextOrGone(params.message)

        negativeButton = findViewById(R.id.alert_negative_button)
        applyButton(negativeButton, params.negativeButton)

        positiveButton = findViewById(R.id.alert_positive_button)
        applyButton(positiveButton, params.positiveButton)

        if (params.coloredButtons) {
            setColoredButton()
        }

        applyAttributes()
    }

    private fun applyAttributes() {
        val attributes = window?.attributes ?: return
        attributes.width = ViewGroup.LayoutParams.MATCH_PARENT
        attributes.height = ViewGroup.LayoutParams.MATCH_PARENT
        attributes.format = android.graphics.PixelFormat.TRANSLUCENT
        attributes.windowAnimations = 0
        window?.attributes = attributes
    }

    private fun setColoredButton() {
        negativeButton.setTextColor(context.getColor(R.color.accentRed))
        positiveButton.setTextColor(context.getColor(R.color.accentBlue))
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val progress = animation.animatedValue as Float
        bodyView.scale = initScale + (1 - initScale) * progress
        bodyView.translationY = initTranslationY * (1 - progress)
        bodyView.alpha = progress
        outsideView.alpha = progress
    }

    private fun applyButton(view: AppCompatTextView, button: ParamButton?) {
        if (button == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = button.title
        view.setOnClickListener {
            button.action?.invoke(this)
            dismiss()
        }
    }

    override fun show() {
        super.show()
        animator.duration = 220
        animator.interpolator = showInterpolator
        animator.start()
    }

    override fun dismiss() {
        animator.duration = 120
        animator.interpolator = hideInterpolator
        animator.doOnEnd { super.dismiss() }
        animator.reverse()
    }

    private data class ParamButton(val title: CharSequence, val action: ((dialog: AlertDialog) -> Unit)?)

    private data class Params(
        val title: CharSequence?,
        val message: CharSequence?,
        val negativeButton: ParamButton?,
        val positiveButton: ParamButton?,
        val coloredButtons: Boolean
    )

    class Builder(private val context: Context) {

        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var negativeButton: ParamButton? = null
        private var positiveButton: ParamButton? = null
        private var coloredButtons: Boolean = false

        fun setTitle(title: CharSequence) = apply {
            this.title = title
        }

        fun setColoredButtons() = apply {
            this.coloredButtons = true
        }

        fun setTitle(resId: Int) = setTitle(context.getString(resId))

        fun setMessage(message: CharSequence) = apply {
            this.message = message
        }

        fun setMessage(resId: Int) = setMessage(context.getString(resId))

        fun setNegativeButton(title: CharSequence, action: (dialog: AlertDialog) -> Unit) = apply {
            this.negativeButton = ParamButton(title, action)
        }

        fun setNegativeButton(resId: Int, action: (dialog: AlertDialog) -> Unit) = setNegativeButton(context.getString(resId), action)

        fun setPositiveButton(title: CharSequence, action: ((dialog: AlertDialog) -> Unit)? = null) = apply {
            this.positiveButton = ParamButton(title, action)
        }

        fun setPositiveButton(resId: Int, action: ((dialog: AlertDialog) -> Unit)? = null) = setPositiveButton(context.getString(resId), action)

        fun build() = AlertDialog(context, Params(title, message, negativeButton, positiveButton, coloredButtons))

        fun show() = build().show()
    }
}