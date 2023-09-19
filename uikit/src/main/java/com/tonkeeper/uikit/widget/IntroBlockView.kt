package com.tonkeeper.uikit.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.tonkeeper.uikit.R

class IntroBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    private val iconView: AppCompatImageView
    private val titleView: AppCompatTextView
    private val descriptionView: AppCompatTextView

    init {
        inflate(context, R.layout.view_intro_block, this)

        iconView = findViewById(R.id.icon)
        titleView = findViewById(R.id.title)
        descriptionView = findViewById(R.id.description)

        context.theme.obtainStyledAttributes(attrs, R.styleable.IntroBlockView,0, 0).apply {
            try {
                initAttrs(this)
            } finally {
                recycle()
            }
        }
    }

    private fun initAttrs(attrs: TypedArray) {
        iconView.setImageDrawable(attrs.getDrawable(R.styleable.IntroBlockView_android_icon))
        titleView.text = attrs.getString(R.styleable.IntroBlockView_android_title)
        descriptionView.text = attrs.getString(R.styleable.IntroBlockView_android_description)
    }
}