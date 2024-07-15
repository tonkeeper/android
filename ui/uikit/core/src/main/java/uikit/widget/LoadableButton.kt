package uikit.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.tonapps.uikit.color.iconSecondaryColor
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.useAttributes

class LoadableButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val button: Button
    private val loaderView: LoaderView

    var isLoading: Boolean = false
        set(value) {
            if (value != field) {
                if (value) {
                    setLoadingState()
                } else {
                    setDefaultState()
                }
            }
            field = value
        }

    var text: CharSequence? = null
        set(value) {
            field = value
            if (!isLoading) {
                button.text = value
            }
        }

    init {
        inflate(context, R.layout.view_loadable_button, this)
        button = findViewById(R.id.loadable_button)
        loaderView = findViewById(R.id.loadable_loader)

        context.useAttributes(attrs, R.styleable.LoadableButton) {
            text = it.getString(R.styleable.LoadableButton_android_text)
        }

        setOnClickListener {
            isLoading = !isLoading
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        button.setOnClickListener(l)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        button.isEnabled = enabled
    }

    private fun setDefaultState() {
        isEnabled = true
        button.text = text
        loaderView.visibility = View.GONE
    }

    private fun setLoadingState() {
        isEnabled = false
        button.text = null
        loaderView.visibility = View.VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val itemHeight = context.getDimensionPixelSize(R.dimen.itemHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(itemHeight, MeasureSpec.EXACTLY))
    }
}