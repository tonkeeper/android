package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import uikit.R
import uikit.drawable.InputDrawable
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.range
import uikit.extensions.scale
import uikit.extensions.useAttributes

class InputFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle),
    View.OnFocusChangeListener,
    TextWatcher,
    ValueAnimator.AnimatorUpdateListener {

    private val reduceHintConfig = HintConfig(
        hintScale = .75f,
        hintTranslationY = (-4f).dp,
        editTextTranslationY = 8f.dp,
    )

    private val expandHintConfig = HintConfig(
        hintScale = 1f,
        hintTranslationY = 0f,
        editTextTranslationY = 0f,
    )

    private var hintReduced = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    hintAnimation.start()
                } else {
                    hintAnimation.reverse()
                }
            }
        }

    private val hintAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 80
        addUpdateListener(this@InputFieldView)
    }

    private val inputDrawable = InputDrawable(context)
    private val hintView: AppCompatTextView
    private val editText: AppCompatEditText
    private val actionView: AppCompatTextView
    private val iconView: AppCompatImageView

    private var actionValue: String? = null
        set(value) {
            field = value
            actionView.text = value
            actionView.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }

    private var iconValue: Int = 0
        set(value) {
            field = value
            iconView.setImageResource(value)
            iconView.visibility = if (value == 0) View.GONE else View.VISIBLE
        }

    init {
        background = inputDrawable
        minimumHeight = context.getDimensionPixelSize(R.dimen.barHeight)
        inflate(context, R.layout.view_input, this)
        hintView = findViewById(R.id.hint)
        editText = findViewById(R.id.input)
        editText.onFocusChangeListener = this
        editText.addTextChangedListener(this)
        actionView = findViewById(R.id.action)
        iconView = findViewById(R.id.icon)
        context.useAttributes(attrs, R.styleable.InputFieldView) {
            hintView.text = it.getString(R.styleable.InputFieldView_android_hint)
            iconValue = it.getResourceId(R.styleable.InputFieldView_android_icon, 0)
            actionValue = it.getString(R.styleable.InputFieldView_android_button)
        }
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        inputDrawable.active = hasFocus
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val progress = animation.animatedValue as Float
        hintView.scale = progress.range(expandHintConfig.hintScale, reduceHintConfig.hintScale)
        hintView.translationY = progress.range(expandHintConfig.hintTranslationY, reduceHintConfig.hintTranslationY)
        editText.translationY = progress.range(expandHintConfig.editTextTranslationY, reduceHintConfig.editTextTranslationY)
    }

    private data class HintConfig(
        val hintScale: Float,
        val hintTranslationY: Float,
        val editTextTranslationY: Float,
    )

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    override fun afterTextChanged(s: Editable?) {
        hintReduced = !s.isNullOrBlank()
    }

}