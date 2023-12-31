package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.doOnNextLayout
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.setPaddingTop
import uikit.extensions.statusBarHeight
import uikit.extensions.useAttributes

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

    fun setTitle(title: String) {
        titleView.text = title
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(64.dp, MeasureSpec.EXACTLY))
    }
}