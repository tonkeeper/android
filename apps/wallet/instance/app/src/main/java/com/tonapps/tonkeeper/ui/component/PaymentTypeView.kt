package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import uikit.extensions.useAttributes
import uikit.widget.RowLayout
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.widget.AsyncImageView

class PaymentTypeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val iconView: AsyncImageView
    private val titleView: AppCompatTextView
    private val subtitleView: AppCompatTextView
    private val checkView: AppCompatImageView

    var title: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    var subtitle: CharSequence?
        get() = subtitleView.text
        set(value) {
            subtitleView.text = value
            if (value.isNullOrBlank()) {
                subtitleView.visibility = GONE
            } else {
                subtitleView.visibility = VISIBLE
            }
        }

    var isChecked: Boolean
        get() = checkView.isVisible
        set(value) {
            checkView.visibility = if (value) {
                VISIBLE
            } else {
                GONE
            }
        }

    init {
        inflate(context, R.layout.view_payment_type, this)
        setPadding(context.getDimensionPixelSize(uikit.R.dimen.offsetMedium))
        iconView = findViewById(R.id.icon)

        titleView = findViewById(R.id.title)
        subtitleView = findViewById(R.id.subtitle)
        checkView = findViewById(R.id.check)

        context.useAttributes(attrs, R.styleable.PaymentTypeView) {
            iconView.setImageDrawable(it.getDrawable(R.styleable.PaymentTypeView_android_icon))
            title = it.getText(R.styleable.PaymentTypeView_android_title)
            subtitle = it.getText(R.styleable.PaymentTypeView_android_subtitle)
            checkView.visibility = if (it.getBoolean(R.styleable.PaymentTypeView_android_checked, false)) {
                VISIBLE
            } else {
                GONE
            }
        }
    }

    fun setRounding(rounding: Boolean) {
        if (rounding) {
            iconView.setScaleTypeCenterCrop()
            iconView.setRound(4f.dp)
            setIconSize(36.dp, 24.dp)
        } else {
            iconView.setScaleTypeCenterInside()
            iconView.setRound(0f)
            setIconSize(36.dp, 36.dp)
        }
    }

    fun setIconSize(width: Int, height: Int) {
        iconView.updateLayoutParams<ViewGroup.LayoutParams> {
            this.width = width
            this.height = height
        }
    }

    fun setIcon(uri: Uri) {
        iconView.setImageURI(uri, this)
    }
}
