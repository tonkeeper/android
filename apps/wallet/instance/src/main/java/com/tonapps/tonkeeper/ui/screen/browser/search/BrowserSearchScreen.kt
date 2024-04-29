package com.tonapps.tonkeeper.ui.screen.browser.search

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.focusWithKeyboard
import uikit.extensions.pinToBottomInsets

class BrowserSearchScreen: BaseFragment(R.layout.fragment_browser_search) {

    private lateinit var searchContainer: View
    private lateinit var searchInput: AppCompatEditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchContainer = view.findViewById(R.id.search_container)
        searchContainer.pinToBottomInsets()

        searchInput = view.findViewById(R.id.search_input)

    }

    override fun onResume() {
        super.onResume()
        searchInput.focusWithKeyboard()
    }

    companion object {
        fun newInstance() = BrowserSearchScreen()
    }
}