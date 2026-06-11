package uikit.chart

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonapps.uikit.color.textPrimaryColor
import uikit.extensions.dp

class ChartPeriodView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    var doOnPeriodSelected: ((period: ChartPeriod) -> Unit)? = null

    private var storedPeriod: ChartPeriod = ChartPeriod.week

    var selectedPeriod: ChartPeriod
        get() = storedPeriod
        set(value) {
            if (storedPeriod != value) {
                storedPeriod = value
                doOnPeriodSelected?.invoke(value)
                updateSelected()
            }
        }

    private val buttonParams = LayoutParams(56.dp, 34.dp)

    init {
        orientation = HORIZONTAL
        ChartPeriod.entries.forEach { period ->
            val button = createButton(period)
            button.setOnClickListener {
                selectedPeriod = period
            }
            addView(button, buttonParams)
        }
        updateSelected()
    }

    /** Updates selection from external state without invoking [doOnPeriodSelected]. */
    fun syncSelectedPeriodFromState(period: ChartPeriod) {
        if (storedPeriod != period) {
            storedPeriod = period
            updateSelected()
        }
    }

    private fun updateSelected() {
        for (i in 0 until childCount) {
            val button = getChildAt(i) as AppCompatTextView
            val period = ChartPeriod.entries[i]
            if (period == storedPeriod) {
                button.setBackgroundResource(uikit.R.drawable.bg_button_secondary_18)
            } else {
                button.background = null
            }
        }
    }

    private fun createButton(period: ChartPeriod): View {
        val view = AppCompatTextView(context)
        view.setTextAppearance(uikit.R.style.TextAppearance_Label2)
        view.setTextColor(context.textPrimaryColor)
        view.gravity = Gravity.CENTER
        view.text = period.title
        return view
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(36.dp, MeasureSpec.EXACTLY))
    }
}
