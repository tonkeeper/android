package com.tonkeeper.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.list.ListCell
import uikit.list.ListCell.Companion.drawable
import uikit.widget.LoaderView

class TransactionDetailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    var position: ListCell.Position = ListCell.Position.SINGLE
        set(value) {
            field = value
            background = value.drawable(context)
        }

    private val titleView: AppCompatTextView
    private val valueView: AppCompatTextView
    private val descriptionView: AppCompatTextView
    private val loaderView: LoaderView

    var title: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    var value: CharSequence?
        get() = valueView.text
        set(value) {
            valueView.text = value
        }

    var description: CharSequence?
        get() = descriptionView.text
        set(value) {
            if (value.isNullOrEmpty()) {
                descriptionView.visibility = GONE
                return
            }
            descriptionView.visibility = VISIBLE
            descriptionView.text = value
        }

    init {
        inflate(context, R.layout.view_send_confirm_item, this)
        setPaddingHorizontal(context.getDimensionPixelSize(uikit.R.dimen.offsetMedium))
        orientation = HORIZONTAL
        minimumHeight = context.getDimensionPixelSize(uikit.R.dimen.itemHeight)

        titleView = findViewById(R.id.title)
        valueView = findViewById(R.id.value)
        descriptionView = findViewById(R.id.description)
        loaderView = findViewById(R.id.loader)
    }

    fun setLoading() {
        valueView.visibility = GONE
        descriptionView.visibility = GONE
        loaderView.visibility = VISIBLE
    }

    fun setDefault() {
        valueView.visibility = VISIBLE
        descriptionView.visibility = VISIBLE
        loaderView.visibility = GONE
    }

    fun setData(value: String, description: String?) {
        setDefault()
        valueView.text = value
        if (description.isNullOrEmpty()) {
            descriptionView.visibility = GONE
        } else {
            descriptionView.visibility = VISIBLE
            descriptionView.text = description
        }
    }

}