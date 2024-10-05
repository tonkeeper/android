package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.icon.UIKitIcon
import uikit.HapticHelper

class CameraFlashIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppCompatImageView(context, attrs, defStyle) {

    private val colorStateList = Color.parseColor("#14ffffff").stateList

    init {
        setBackgroundResource(uikit.R.drawable.bg_oval)
        setImageResource(UIKitIcon.ic_flash)
        backgroundTintList = colorStateList
        scaleType = ScaleType.CENTER
        setFlashState(false)
    }

    fun setFlashState(enabled: Boolean) {
        if (enabled) {
            applyActiveState()
        } else {
            applyDefaultState()
        }
    }

    private fun applyActiveState() {
        visibility = View.VISIBLE
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        imageTintList = ColorStateList.valueOf(Color.BLACK)
    }

    private fun applyDefaultState() {
        visibility = View.VISIBLE
        backgroundTintList = colorStateList
        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
}