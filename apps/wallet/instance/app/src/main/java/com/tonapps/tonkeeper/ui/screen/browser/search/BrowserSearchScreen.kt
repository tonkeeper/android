package com.tonapps.tonkeeper.ui.screen.browser.search

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.safe.DAppSafeScreen
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Adapter
import com.tonapps.tonkeeper.ui.screen.browser.search.list.Item
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectSafeModeDialog
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.drawable.FooterDrawable
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.focusWithKeyboard
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.hideKeyboard
import uikit.extensions.inflate
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.HeaderView

class BrowserSearchScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_browser_search, wallet) {

    override val fragmentName: String = "BrowserSearchScreen"

    private val rootViewMode: RootViewModel by activityViewModel()

    override val viewModel: BrowserSearchViewModel by viewModel()

    private val adapter = Adapter { title, url, iconUrl, sendAnalytics ->
        val uri = url.toUriOrNull() ?: return@Adapter
        if (uri.host?.endsWith("mercuryo.io") == true) {
            BrowserHelper.open(requireContext(), url)
        } else {
            navigation?.add(DAppScreen.newInstance(
                wallet = screenContext.wallet,
                title = title,
                url = url.toUri(),
                iconUrl = iconUrl,
                source = "browser_search"
            ))
        }
        finish()
    }

    private lateinit var headerView: HeaderView
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
        headerView = view.findViewById(R.id.header)

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

        view.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            finish()
        }
    }

    private fun inputDone() {
        val query = searchInput.text.toString()
        val uri = BrowserSearchViewModel.parseIfUrl(query)?.let {
            DeepLinkRoute.normalize(it)
        } ?: viewModel.createSearchUrl(query)

        if (uri.scheme == "tonkeeper") {
            rootViewMode.processDeepLink(uri, false, Uri.EMPTY, false, requireContext().packageName)
        } else {
            lifecycleScope.launch {
                if (viewModel.isScamUri(uri)) {
                    navigation?.add(DAppSafeScreen.newInstance(wallet))
                } else {
                    navigation?.add(DAppScreen.newInstance(
                        wallet = wallet,
                        title = uri.host ?: "unknown",
                        url = uri,
                        iconUrl = "",
                        source = "browser_search_direct"
                    ))
                }
            }
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