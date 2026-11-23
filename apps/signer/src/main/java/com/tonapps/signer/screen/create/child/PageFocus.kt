package com.tonapps.signer.screen.create.child

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tonapps.signer.screen.create.CreateViewModel
import com.tonapps.signer.screen.create.pager.PageType
import uikit.extensions.collectFlow

internal fun Fragment.keepFocusInsidePage(
    createViewModel: CreateViewModel,
    pageType: PageType,
    focusPage: () -> Unit,
) {
    collectFlow(createViewModel.currentPage) { currentPage ->
        val pageView = view as? ViewGroup ?: return@collectFlow
        val isCurrentPage = currentPage == pageType
        if (isCurrentPage) {
            pageView.focusable = View.FOCUSABLE_AUTO
            pageView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            focusPage()
        } else {
            pageView.focusable = View.NOT_FOCUSABLE
            pageView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }
}
