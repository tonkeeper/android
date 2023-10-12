package com.tonkeeper.uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.dp
import com.tonkeeper.uikit.extensions.useAttributes

class IconButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val iconView: AppCompatImageView
    private val titleView: AppCompatTextView

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_icon_button, this)
        iconView = findViewById(R.id.icon)
        titleView = findViewById(R.id.title)

        context.useAttributes(attrs, R.styleable.IconButtonView) {
            iconView.setImageResource(it.getResourceId(R.styleable.IconButtonView_android_icon, 0))
            titleView.text = it.getString(R.styleable.IconButtonView_android_title)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(72.dp, MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}