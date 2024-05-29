package uikit.dialog.alert

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.accentRedColor
import uikit.R
import uikit.base.BaseDialog
import uikit.extensions.dp
import uikit.extensions.scale
import uikit.extensions.setTextOrGone

class AlertDialog private constructor(
    context: Context,
    params: Params
): BaseDialog(context), ValueAnimator.AnimatorUpdateListener {

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
        if (button.color != Color.TRANSPARENT) {
            view.setTextColor(button.color)
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

    private data class ParamButton(
        val title: CharSequence,
        val color: Int,
        val action: ((dialog: AlertDialog) -> Unit)?
    )

    private data class Params(
        val title: CharSequence?,
        val message: CharSequence?,
        val negativeButton: ParamButton?,
        val positiveButton: ParamButton?
    )

    class Builder(private val context: Context) {

        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var negativeButton: ParamButton? = null
        private var positiveButton: ParamButton? = null

        fun setTitle(title: CharSequence) = apply {
            this.title = title
        }

        fun setTitle(resId: Int) = setTitle(context.getString(resId))

        fun setMessage(message: CharSequence) = apply {
            this.message = message
        }

        fun setMessage(resId: Int) = setMessage(context.getString(resId))

        fun setNegativeButton(
            title: CharSequence,
            color: Int = Color.TRANSPARENT,
            action: ((dialog: AlertDialog) -> Unit)? = null
        ) = apply {
            this.negativeButton = ParamButton(title, color, action)
        }

        fun setNegativeButton(
            resId: Int,
            color: Int = Color.TRANSPARENT,
            action: ((dialog: AlertDialog) -> Unit)? = null
        ) = setNegativeButton(context.getString(resId), color, action)

        fun setPositiveButton(
            title: CharSequence,
            color: Int = Color.TRANSPARENT,
            action: ((dialog: AlertDialog) -> Unit)? = null,
        ) = apply {
            this.positiveButton = ParamButton(title, color, action)
        }

        fun setPositiveButton(
            resId: Int,
            color: Int = Color.TRANSPARENT,
            action: ((dialog: AlertDialog) -> Unit)? = null
        ) = setPositiveButton(context.getString(resId), color, action)

        fun build() = AlertDialog(context, Params(title, message, negativeButton, positiveButton))

        fun show() = build().show()
    }
}