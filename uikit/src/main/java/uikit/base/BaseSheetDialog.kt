package com.tonkeeper.uikit.base

import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tonkeeper.uikit.R

open class BaseSheetDialog(
    context: Context
): BottomSheetDialog(context) {

    private val closeView: AppCompatImageView
    private val contentView: FrameLayout

    init {
        super.setContentView(R.layout.dialog_base)
        closeView = super.findViewById(R.id.close)!!
        closeView.setOnClickListener {
            dismiss()
        }
        contentView = super.findViewById(R.id.content)!!
    }

    override fun setContentView(layoutResID: Int) {
        contentView.removeAllViews()
        layoutInflater.inflate(layoutResID, contentView, true)
    }

}