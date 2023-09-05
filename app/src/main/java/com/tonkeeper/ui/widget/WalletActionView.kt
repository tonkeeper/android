package com.tonkeeper.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.R
import com.tonkeeper.extensions.dp

class WalletActionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val iconView: AppCompatImageView
    private val titleView: AppCompatTextView

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_wallet_action, this)

        iconView = findViewById(R.id.icon)
        iconView.setOnClickListener {

        }
        titleView = findViewById(R.id.title)

        context.theme.obtainStyledAttributes(attrs, R.styleable.WalletActionView,0, 0).apply {
            try {
                initAttrs(this)
            } finally {
                recycle()
            }
        }
    }

    private fun initAttrs(attrs: TypedArray) {
        iconView.setImageResource(attrs.getResourceId(R.styleable.WalletActionView_android_icon, 0))
        titleView.text = attrs.getString(R.styleable.WalletActionView_android_title)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(72.dp, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(84.dp, MeasureSpec.EXACTLY))
    }

}