package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.tonapps.uikit.flag.getFlagDrawable
import uikit.extensions.asCircle
import uikit.extensions.circle
import uikit.extensions.getDrawable

class CountryFlagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppCompatImageView(context, attrs, defStyle) {

    init {
        circle()
        setBackgroundResource(uikit.R.drawable.bg_button_secondary)
    }

    fun setCountry(code: String) {
        val resId = getFlagDrawable(code)
        if (resId == null) {
            setImageDrawable(null)
        } else {
            setIcon(resId)
        }
    }

    fun setIcon(resId: Int) {
        val drawable = getDrawable(resId)
        if (paddingTop == 0) {
            setImageDrawable(drawable)
        } else {
            setImageDrawable(drawable.asCircle())
        }
    }

    override fun hasOverlappingRendering() = false
}