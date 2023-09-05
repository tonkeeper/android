package com.tonkeeper.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.R

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
        setBackgroundResource(R.color.primaryDark)
        inflate(context, R.layout.view_header, this)

        titleView = findViewById(R.id.title)
        actionView = findViewById(R.id.action)

        context.theme.obtainStyledAttributes(attrs, R.styleable.HeaderView,0, 0).apply {
            try {
                initAttrs(this)
            } finally {
                recycle()
            }
        }
    }

    private fun initAttrs(attrs: TypedArray) {
        titleView.text = attrs.getString(R.styleable.HeaderView_android_title)

        val actionResId = attrs.getResourceId(R.styleable.HeaderView_android_action, 0)
        if (actionResId != 0) {
            actionView.setImageResource(actionResId)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(context.resources.getDimension(R.dimen.view_bar_height).toInt(), MeasureSpec.EXACTLY))
    }
}