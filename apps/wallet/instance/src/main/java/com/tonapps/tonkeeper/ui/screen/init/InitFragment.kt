package com.tonapps.tonkeeper.ui.screen.init

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.ui.screen.init.pager.PagerAdapter
import com.tonapps.uikit.color.backgroundPageColor
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.runAnimation
import uikit.extensions.withAlpha
import uikit.widget.HeaderView

class InitFragment: BaseFragment(R.layout.fragment_init), BaseFragment.SwipeBack {

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

    private val initViewModel: InitViewModel by viewModel { parametersOf(argsAction, argsName, argsPkBase64) }

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2
    private lateinit var loaderView: View
    private lateinit var loaderIconView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (argsName.isNotBlank()) {
            initViewModel.setName(argsName)
        }
        if (argsPkBase64.isNotBlank()) {
            initViewModel.setPublicKey(argsPkBase64)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.setBackgroundResource(uikit.R.drawable.bg_page_gradient)
        headerView.doOnLayout { initViewModel.setUiTopOffset(it.measuredHeight) }
        headerView.doOnCloseClick = {
            if (!initViewModel.prev()) {
                finish()
            }
        }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = PagerAdapter(this, initViewModel.pages, argsName)
        pagerView.offscreenPageLimit = initViewModel.pages.size

        loaderView = view.findViewById(R.id.loader)
        loaderView.setBackgroundColor(requireContext().backgroundPageColor.withAlpha(.64f))
        loaderView.setOnClickListener {  }

        loaderIconView = view.findViewById(R.id.loader_icon)
        loaderIconView.runAnimation(R.anim.gear_loading)

        collectFlow(initViewModel.currentPage, ::setCurrentPage)
    }

    private fun setCurrentPage(newIndex: Int) {
        val oldIndex = pagerView.currentItem
        if (oldIndex != newIndex) {
            pagerView.setCurrentItem(newIndex, true)
        }
    }
}