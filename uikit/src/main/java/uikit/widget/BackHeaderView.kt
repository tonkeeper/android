package com.tonkeeper.uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.doOnNextLayout
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.dp
import com.tonkeeper.uikit.extensions.getDimensionPixelSize
import com.tonkeeper.uikit.extensions.setPaddingHorizontal
import com.tonkeeper.uikit.extensions.setPaddingTop
import com.tonkeeper.uikit.extensions.statusBarHeight
import com.tonkeeper.uikit.extensions.useAttributes

class BackHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    var doOnBackClick: (() -> Unit)? = null

    private val backView: AppCompatImageView
    private val titleView: AppCompatTextView
    private val actionView: AppCompatImageView

    init {
        orientation = HORIZONTAL
        inflate(context, R.layout.view_back_header, this)
        setBackgroundResource(R.drawable.bg_page_gradient)
        setPaddingHorizontal(context.getDimensionPixelSize(R.dimen.offsetMedium))
        setPaddingTop(context.statusBarHeight)

        backView = findViewById(R.id.back)
        titleView = findViewById(R.id.title)
        actionView = findViewById(R.id.action)

        backView.setOnClickListener {
            doOnBackClick?.invoke()
        }

        context.useAttributes(attrs, R.styleable.BackHeaderView) {
            titleView.text = it.getString(R.styleable.BackHeaderView_android_title)
        }
    }

    fun bindContentPadding(view: View) {
        view.setPaddingTop(measuredHeight)
        doOnNextLayout {
            view.setPaddingTop(measuredHeight)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(64.dp + context.statusBarHeight, MeasureSpec.EXACTLY))
    }
}