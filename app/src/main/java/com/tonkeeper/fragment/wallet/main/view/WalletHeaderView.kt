package com.tonkeeper.fragment.wallet.main.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.tonkeeper.R
import uikit.extensions.dp
import uikit.extensions.getColor
import uikit.extensions.getDrawable
import uikit.extensions.setEndDrawable
import uikit.widget.HeaderView

class WalletHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : HeaderView(context, attrs, defStyle) {

    var updating: Boolean = false
        set(value) {
            if (value != field) {
                if (value) {
                    setUpdating(R.string.updating)
                } else {
                    setDefault()
                }
                field = value
            }
        }

    var doOnTitleClick: (() -> Unit)? = null

    init {
        actionView.background = null
        actionView.imageTintList = ColorStateList.valueOf(getColor(uikit.R.color.accentBlue))

        val downDrawable = getDrawable(R.drawable.ic_chevron_down_16)
        downDrawable.setTint(getColor(uikit.R.color.iconSecondary))
        titleView.compoundDrawablePadding = 6.dp
        titleView.setEndDrawable(downDrawable)
        titleView.setOnClickListener { doOnTitleClick?.invoke() }
    }


}