package com.tonapps.tonkeeper.extensions

import uikit.base.BaseFragment
import uikit.extensions.hideKeyboard

fun BaseFragment.hideKeyboard() {
    requireActivity().hideKeyboard()
}

fun BaseFragment.finishDelay(delay: Long = 3000) {
    postDelayed(delay) { finish() }
}