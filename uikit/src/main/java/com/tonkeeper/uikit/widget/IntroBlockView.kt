package com.tonkeeper.uikit.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.useAttributes

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

        context.useAttributes(attrs, R.styleable.IntroBlockView) {
            iconView.setImageDrawable(it.getDrawable(R.styleable.IntroBlockView_android_icon))
            titleView.text = it.getString(R.styleable.IntroBlockView_android_title)
            descriptionView.text = it.getString(R.styleable.IntroBlockView_android_description)
        }
    }
}