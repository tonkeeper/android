package com.tonapps.tonkeeper.ui.screen.browser.search

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Adapter
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.extensions.isMaxScrollReached
import uikit.navigation.Navigation.Companion.navigation
import uikit.utils.RecyclerVerticalScrollListener

class BrowserSearchScreen: BaseFragment(R.layout.fragment_browser_search) {

    private val searchViewModel: BrowserSearchViewModel by viewModel()
    private val adapter = Adapter { title, url ->
        val host = Uri.parse(url).host ?: url
        navigation?.add(DAppScreen.newInstance(title, host, url))
        finish()
    }

    private lateinit var footerDrawable: FooterDrawable
    private lateinit var searchContainer: View
    private lateinit var searchInput: AppCompatEditText
    private lateinit var contentView: View
    private lateinit var placeholderView: AppCompatTextView
    private lateinit var listView: RecyclerView

    private val scrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            footerDrawable.setDivider(!recyclerView.isMaxScrollReached)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        footerDrawable = FooterDrawable(requireContext())
        footerDrawable.setColor(requireContext().backgroundTransparentColor)

        searchContainer = view.findViewById(R.id.search_container)
        searchContainer.doKeyboardAnimation { offset, progress, isShowing ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = offset
            }
            if (!isShowing) {
                finish()
            }
        }

        searchInput = view.findViewById(R.id.search_input)
        searchInput.doAfterTextChanged { searchViewModel.query(it.toString()) }
        contentView = view.findViewById(R.id.content)

        placeholderView = view.findViewById(R.id.placeholder)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(searchViewModel.uiItemsFlow) {
            submitList(it)
            placeholderView.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun submitList(items: List<Item>) {
        adapter.submitList(items) {
            listView.scrollToPosition(0)
        }
    }

    override fun onPause() {
        super.onPause()
        scrollListener.detach()
    }

    override fun onResume() {
        super.onResume()
        searchInput.focusWithKeyboard()
        scrollListener.attach(listView)
    }

    companion object {
        fun newInstance() = BrowserSearchScreen()
    }
}