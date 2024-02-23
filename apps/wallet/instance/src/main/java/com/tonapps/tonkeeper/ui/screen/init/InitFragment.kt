package com.tonapps.tonkeeper.ui.screen.init

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.doOnLayout
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.extensions.launch
import com.tonapps.tonkeeper.fragment.root.RootActivity
import com.tonapps.tonkeeper.fragment.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.init.pager.PagerAdapter
import kotlinx.coroutines.flow.filter
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class InitFragment: BaseFragment(R.layout.fragment_pager), BaseFragment.SwipeBack {

    companion object {

        private const val ACTION_KEY = "action"
        private const val NAME_KEY = "name"
        private const val PK_BASE64_KEY = "pk"

        fun newInstance(
            action: InitAction,
            name: String? = null,
            pkBase64: String? = null,
        ): InitFragment {
            val fragment = InitFragment()
            fragment.arguments = Bundle().apply {
                putInt(ACTION_KEY, action.ordinal)
                putString(NAME_KEY, name)
                putString(PK_BASE64_KEY, pkBase64)
            }
            return fragment
        }

        fun singer(uri: Uri): InitFragment? {
            val pkBase64 = uri.getQueryParameter("pk") ?: return null
            val name = uri.getQueryParameter("name")
            return newInstance(InitAction.Signer, name, pkBase64)
        }
    }

    private val argsAction: InitAction by lazy {
        val value = arguments?.getInt(ACTION_KEY) ?: 0
        InitAction.entries[value]
    }

    private val argsName: String by lazy {
        arguments?.getString(NAME_KEY) ?: ""
    }

    private val argsPkBase64: String by lazy {
        arguments?.getString(PK_BASE64_KEY) ?: ""
    }

    private val viewModel: InitViewModel by viewModel { parametersOf(argsAction, argsName, argsPkBase64) }
    private val rootViewModel: RootViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.setBackgroundResource(uikit.R.drawable.bg_page_gradient)
        headerView.doOnLayout { viewModel.setUiTopOffset(it.measuredHeight) }
        headerView.doOnCloseClick = {
            if (!viewModel.prev()) {
                finish()
            }
        }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = PagerAdapter(this, viewModel.pages, argsName)
        pagerView.offscreenPageLimit = viewModel.pages.size

        collectFlow(viewModel.currentPage, ::setCurrentPage)
        collectFlow(viewModel.savedWalletFlow) {
            (activity as RootActivity).init(hasWallet = true, recreate = true)
        }
    }

    private fun setCurrentPage(newIndex: Int) {
        val oldIndex = pagerView.currentItem
        if (oldIndex != newIndex) {
            pagerView.setCurrentItem(newIndex, true)
        }
    }
}