package com.tonapps.signer.screen.create.pager

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

class KeyScrollBlockingPageLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return super.dispatchKeyEvent(event) || event.keyCode in pagerScrollKeyCodes
    }
}

private val pagerScrollKeyCodes = setOf(
    KeyEvent.KEYCODE_PAGE_UP,
    KeyEvent.KEYCODE_PAGE_DOWN,
    KeyEvent.KEYCODE_MOVE_HOME,
    KeyEvent.KEYCODE_MOVE_END,
)
