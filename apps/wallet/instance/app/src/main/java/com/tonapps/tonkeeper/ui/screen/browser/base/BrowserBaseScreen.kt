package com.tonapps.tonkeeper.ui.screen.browser.base

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainScreen
import com.tonapps.tonkeeper.ui.screen.browser.more.BrowserMoreScreen
import com.tonapps.tonkeeper.ui.screen.browser.search.BrowserSearchScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize

class BrowserBaseScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_browser_base, wallet) {

    override val fragmentName: String = "BrowserBaseScreen"

    override val viewModel: BrowserBaseViewModel by walletViewModel()

    private lateinit var footerDrawable: FooterDrawable
    private lateinit var searchContainerView: View
    private lateinit var searchView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        footerDrawable = FooterDrawable(requireContext())

        searchContainerView = view.findViewById(R.id.search_container)
        searchContainerView.background = footerDrawable

        searchView = view.findViewById(R.id.search)
        searchView.setOnClickListener {
            navigation?.add(BrowserSearchScreen.newInstance(screenContext.wallet))
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, ::onApplyWindowInsets)

        collectFlow(viewModel.childBottomScrolled, footerDrawable::setDivider)

        setPrimaryFragment()
    }

    private fun setPrimaryFragment() {
        val fragment = BrowserMainScreen.newInstance(screenContext.wallet)
        childFragmentManager.commit {
            replace(CONTAINER_ID, fragment)
        }
    }

    fun addFragment(fragment: BaseFragment) {
        childFragmentManager.commitNow {
            add(CONTAINER_ID, fragment)
        }
    }

    fun removeFragment(fragment: BaseFragment) {
        childFragmentManager.commitNow {
            remove(fragment)
        }
    }

    fun openCategory(category: String) {
        lifecycleScope.launch {
            if (viewModel.hasCategory(category)) {
                val fragment = BrowserMoreScreen.newInstance(screenContext.wallet, category)
                addFragment(fragment)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        val lastFragment = childFragmentManager.fragments.lastOrNull() as? BaseFragment
        if (lastFragment !is BrowserMainScreen) {
            lastFragment?.finish()
            return false
        }
        return true
    }

    private fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        viewModel.setInsetsRoot(insets)
        val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navInsets.bottom + view.context.getDimensionPixelSize(uikit.R.dimen.barHeight)
        }
        return insets
    }

    companion object {

        private val CONTAINER_ID = R.id.browser_fragment_container

        fun newInstance(wallet: WalletEntity) = BrowserBaseScreen(wallet)

        fun from(fragment: Fragment): BrowserBaseScreen? {
            if (fragment is BrowserBaseScreen) {
                return fragment
            }
            return fragment.parentFragment?.let {
                from(it)
            }
        }
    }
}