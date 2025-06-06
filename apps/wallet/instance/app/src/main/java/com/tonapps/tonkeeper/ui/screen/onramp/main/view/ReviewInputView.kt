package com.tonapps.tonkeeper.ui.screen.onramp.main.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.tonapps.tonkeeperx.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.useAttributes
import uikit.widget.ColumnLayout

class ReviewInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ColumnLayout(context, attrs, defStyle) {

    private val offsetMedium = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)

    private val titleView: AppCompatTextView
    private val valueView: AppCompatTextView

    init {
        inflate(context, R.layout.view_review_input, this)
        setPadding(offsetMedium)
        setBackgroundResource(uikit.R.drawable.bg_content)

        titleView = findViewById(R.id.review_title)
        valueView = findViewById(R.id.review_value)

        context.useAttributes(attrs, R.styleable.ReviewInputView) {
            titleView.text = it.getString(R.styleable.ReviewInputView_android_title)
        }
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setValue(value: CharSequence) {
        valueView.text = value
    }
}