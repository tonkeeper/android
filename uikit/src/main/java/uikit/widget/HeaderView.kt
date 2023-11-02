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

class HeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val closeView: AppCompatImageView
    private val titleView: AppCompatTextView
    private val subtitleContainerView: View
    private val actionView: AppCompatImageView
    private val subtitleView: AppCompatTextView
    private val loaderView: LoaderView

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


    var title: String
        get() = titleView.text.toString()
        set(value) {
            titleView.text = value
        }

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.color.backgroundPage)
        setPaddingHorizontal(context.getDimensionPixelSize(R.dimen.offsetMedium))

        inflate(context, R.layout.view_header, this)

        subtitleContainerView = findViewById(R.id.subtitle_container)
        closeView = findViewById(R.id.close)
        titleView = findViewById(R.id.title)
        actionView = findViewById(R.id.action)
        subtitleView = findViewById(R.id.subtitle)
        loaderView = findViewById(R.id.loader)

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

    private fun setDrawableForView(view: AppCompatImageView, @DrawableRes resId: Int) {
        if (resId == 0) {
            view.alpha = 0f
        } else {
            view.setImageResource(resId)
            view.alpha = 1f
        }
    }

    fun setUpdating(@StringRes textResId: Int) {
        showSubtitle(textResId)
        loaderView.resetAnimation()
    }

    fun setDefault() {
        withAnimation {
            subtitleContainerView.visibility = View.GONE
            loaderView.stopAnimation()
        }
    }

    fun showSubtitle(@StringRes textResId: Int) {
        showSubtitle(context.getString(textResId))
    }

    fun showSubtitle(text: String) {
        withAnimation {
            subtitleContainerView.visibility = View.VISIBLE
            subtitleView.text = text
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = context.getDimensionPixelSize(R.dimen.barHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

}