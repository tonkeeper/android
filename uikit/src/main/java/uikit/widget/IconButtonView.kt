package uikit.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.drawable.CellBackgroundDrawable
import uikit.extensions.createRipple
import uikit.extensions.dp
import uikit.extensions.useAttributes

class IconButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val iconView: AppCompatImageView
    private val titleView: AppCompatTextView
    private val ripple = context.createRipple()

    init {
        inflate(context, R.layout.view_icon_button, this)
        background = ripple

        iconView = findViewById(R.id.icon)
        titleView = findViewById(R.id.title)

        context.useAttributes(attrs, R.styleable.IconButtonView) {
            iconView.setImageResource(it.getResourceId(R.styleable.IconButtonView_android_icon, 0))
            titleView.text = it.getString(R.styleable.IconButtonView_android_title)
            isEnabled = it.getBoolean(R.styleable.IconButtonView_android_enabled, true)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        ripple.radius = measuredHeight / 2
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else .2f
    }
}