package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.useAttributes

class ActionCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val iconView: AppCompatImageView
    private val titleView: AppCompatTextView
    private val subtitleView: AppCompatTextView

    init {
        setPadding(context.getDimensionPixelSize(R.dimen.offsetMedium))
        setBackgroundResource(R.drawable.bg_content)
        inflate(context, R.layout.view_action_cell, this)

        iconView = findViewById(R.id.action_cell_icon)
        titleView = findViewById(R.id.action_cell_title)
        subtitleView = findViewById(R.id.action_cell_subtitle)

        context.useAttributes(attrs, R.styleable.ActionCellView) {
            val iconResId = it.getResourceId(R.styleable.HeaderView_android_icon, 0)
            iconView.setImageResource(iconResId)

            titleView.text = it.getString(R.styleable.ActionCellView_android_title)
            subtitleView.text = it.getString(R.styleable.ActionCellView_android_subtitle)
        }
    }
}