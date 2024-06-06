package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.useAttributes
import uikit.extensions.withAnimation

class ProcessTaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    enum class State {
        LOADING,
        SUCCESS,
        FAILED
    }

    private val loaderView: LoaderView

    private val successView: View
    private val successLabelView: AppCompatTextView

    private val failedView: View
    private val failedLabelView: AppCompatTextView

    var state: State = State.LOADING
        set(value) {
            if (field != value) {
                field = value
                applyState(value)
            }
        }

    init {
        inflate(context, R.layout.view_process_task, this)
        loaderView = findViewById(R.id.loader)

        successView = findViewById(R.id.success)
        successLabelView = findViewById(R.id.success_label)

        failedView = findViewById(R.id.failed)
        failedLabelView = findViewById(R.id.failed_label)

        context.useAttributes(attrs, R.styleable.ProcessTaskView) {
            successLabelView.text = it.getString(R.styleable.ProcessTaskView_successLabel)
            failedLabelView.text = it.getString(R.styleable.ProcessTaskView_errorLabel)
        }
    }

    fun setFailedLabel(label: String) {
        failedLabelView.text = label
    }

    private fun applyState(state: State) {
        withAnimation {
            when (state) {
                State.LOADING -> setLoading()
                State.SUCCESS -> setSuccess()
                State.FAILED -> setFailed()
            }
        }
    }

    private fun setLoading() {
        loaderView.visibility = View.VISIBLE
        successView.visibility = View.GONE
        failedView.visibility = View.GONE
    }

    private fun setSuccess() {
        loaderView.visibility = View.GONE
        successView.visibility = View.VISIBLE
        failedView.visibility = View.GONE
    }

    private fun setFailed() {
        loaderView.visibility = View.GONE
        successView.visibility = View.GONE
        failedView.visibility = View.VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = context.getDimensionPixelSize(R.dimen.itemHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }

}