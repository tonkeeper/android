package com.tonkeeper.fragment.wallet.init

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeperx.R
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.wallet.init.pager.PagerAdapter
import kotlinx.coroutines.flow.filter
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class InitScreen: BaseFragment(R.layout.fragment_pager), BaseFragment.SwipeBack {

    companion object {

        private const val ACTION_KEY = "action"
        private const val NAME_KEY = "name"
        private const val PK_BASE64_KEY = "pk"

        fun newInstance(
            action: InitAction,
            name: String? = null,
            pkBase64: String? = null,
        ): InitScreen {
            val fragment = InitScreen()
            fragment.arguments = Bundle().apply {
                putInt(ACTION_KEY, action.ordinal)
                putString(NAME_KEY, name)
                putString(PK_BASE64_KEY, pkBase64)
            }
            return fragment
        }

    }

    private val argsAction: InitAction by lazy {
        val value = arguments?.getInt(ACTION_KEY) ?: 0
        InitAction.entries[value]
    }

    private val argsName: String? by lazy {
        arguments?.getString(NAME_KEY)
    }

    private val argsPkBase64: String? by lazy {
        arguments?.getString(PK_BASE64_KEY)
    }

    private val viewModel: InitModel by viewModels(
        factoryProducer = { InitModel.Factory(argsAction, argsName, argsPkBase64) },
    )

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = PagerAdapter(this, viewModel.pages, argsName)

        viewModel.currentPage.launch(this) {
            pagerView.setCurrentItem(it, true)
        }

        viewModel.ready.filter { it }.launch(this) {
            navigation?.initRoot(true)
        }
    }
}