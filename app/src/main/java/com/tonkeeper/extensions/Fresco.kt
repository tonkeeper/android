package com.tonkeeper.extensions

import androidx.annotation.DrawableRes
import com.facebook.drawee.view.SimpleDraweeView

fun SimpleDraweeView.setImageRes(@DrawableRes id: Int) {
    setImageURI("res:///$id")
}