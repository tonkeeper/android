package com.tonapps.tonkeeper.ui.screen.browser.search

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Adapter
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.drawable.FooterDrawable
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.focusWithKeyboard
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.hideKeyboard
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener

class BrowserSearchScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_browser_search, wallet) {

    override val viewModel: BrowserSearchViewModel by viewModel()

    private val adapter = Adapter { title, url ->
        navigation?.add(DAppScreen.newInstance(
            wallet = screenContext.wallet,
            title = title,
            url = url.toUri(),
            source = "browser_search"
        ))
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
        searchContainer.doKeyboardAnimation { offset, _, isShowing ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = offset
            }
            if (!isShowing) {
                view.postDelayed({
                    finishAfterHideKeyboard()
                }, 300)
            }
        }

        searchInput = view.findViewById(R.id.search_input)
        searchInput.doAfterTextChanged { viewModel.query(it.toString()) }
        searchInput.onEditorAction(EditorInfo.IME_ACTION_DONE)
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                inputDone()
                true
            } else {
                false
            }
        }
        contentView = view.findViewById(R.id.content)

        view.findViewById<View>(R.id.search_icon).setOnClickListener { searchInput.hideKeyboard() }

        placeholderView = view.findViewById(R.id.placeholder)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(viewModel.uiItemsFlow) {
            submitList(it)
            placeholderView.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun inputDone() {
        val query = searchInput.text.toString()
        val url = BrowserSearchViewModel.parseIfUrl(query)
        if (url != null) {
            navigation?.add(DAppScreen.newInstance(
                wallet = wallet,
                url = url,
                source = "browser_search_direct"
            ))
        } else {
            navigation?.add(DAppScreen.newInstance(
                wallet = wallet,
                url = viewModel.createSearchUrl(query),
                source = "browser_search_direct"
            ))
        }
        searchInput.hideKeyboard()
    }

    private fun finishAfterHideKeyboard() {
        if (adapter.itemCount > 0) {
            return
        }
        val windowInsets = searchInput.getRootWindowInsetsCompat() ?: return
        if (!windowInsets.isVisible(WindowInsetsCompat.Type.ime())) {
            finish()
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
        fun newInstance(wallet: WalletEntity) = BrowserSearchScreen(wallet)
    }
}