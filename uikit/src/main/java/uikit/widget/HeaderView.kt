package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.graphics.alpha
import androidx.transition.TransitionManager
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.useAttributes
import uikit.extensions.withAnimation

open class HeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    val closeView: AppCompatImageView
    val actionView: AppCompatImageView
    val titleView: AppCompatTextView

    private val subtitleContainerView: View
    private val subtitleView: AppCompatTextView
    private val loaderView: LoaderView
    private val textView: View

    var doOnCloseClick: (() -> Unit)? = null
        set(value) {
            field = value
            closeView.setOnClickListener { value?.invoke() }
        }

    var doOnActionClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionView.setOnClickListener { value?.invoke() }
        }


    var title: CharSequence
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    init {
        orientation = HORIZONTAL
        super.setBackgroundResource(R.color.backgroundPage)
        setPaddingHorizontal(context.getDimensionPixelSize(R.dimen.offsetMedium))

        inflate(context, R.layout.view_header, this)

        subtitleContainerView = findViewById(R.id.subtitle_container)
        closeView = findViewById(R.id.header_close)
        titleView = findViewById(R.id.header_title)
        actionView = findViewById(R.id.header_action)
        subtitleView = findViewById(R.id.header_subtitle)
        loaderView = findViewById(R.id.header_loader)
        textView = findViewById(R.id.header_text)

        context.useAttributes(attrs, R.styleable.HeaderView) {
            val iconResId = it.getResourceId(R.styleable.HeaderView_android_icon, 0)
            setIcon(iconResId)

            titleView.text = it.getString(R.styleable.HeaderView_android_title)

            val actionResId = it.getResourceId(R.styleable.HeaderView_android_action, 0)
            setAction(actionResId)
        }
    }

    fun setIcon(@DrawableRes resId: Int) {
        setDrawableForView(closeView, resId)
    }

    fun setAction(@DrawableRes resId: Int) {
        setDrawableForView(actionView, resId)
    }

    fun contentMatchParent() {
        titleView.layoutParams = titleView.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        subtitleContainerView.layoutParams = subtitleContainerView.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        subtitleView.layoutParams = subtitleView.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun setDrawableForView(view: AppCompatImageView, @DrawableRes resId: Int) {
        if (resId == 0) {
            view.alpha = 0f
        } else {
            view.setImageResource(resId)
            view.alpha = 1f
        }
    }

    fun setUpdating(@StringRes textResId: Int) {
        setSubtitle(textResId)
        loaderView.resetAnimation()
    }

    fun setDefault() {
        withAnimation {
            subtitleContainerView.visibility = View.GONE
            loaderView.stopAnimation()
        }
    }

    fun setSubtitle(@StringRes textResId: Int) {
        setSubtitle(context.getString(textResId))
    }

    fun setSubtitle(text: CharSequence?) {
        withAnimation {
            subtitleContainerView.visibility = if (text.isNullOrEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            subtitleView.text = text
        }
    }

    fun hideText() {
        withAnimation {
            textView.alpha = 0f
        }
    }

    fun showText() {
        withAnimation {
            textView.alpha = 1f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = context.getDimensionPixelSize(R.dimen.barHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

}