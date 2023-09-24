package com.tonkeeper.uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.useAttributes

class HeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val titleView: AppCompatTextView
    private val actionView: AppCompatImageView

    var doOnActionClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionView.setOnClickListener { value?.invoke() }
        }

    init {
        orientation = HORIZONTAL
        inflate(context, R.layout.view_header, this)

        titleView = findViewById(R.id.title)
        actionView = findViewById(R.id.action)

        context.useAttributes(attrs, R.styleable.HeaderView) {
            titleView.text = it.getString(R.styleable.HeaderView_android_title)

            val actionResId = it.getResourceId(R.styleable.HeaderView_android_action, 0)
            if (actionResId != 0) {
                actionView.setImageResource(actionResId)
            }
        }
    }

}