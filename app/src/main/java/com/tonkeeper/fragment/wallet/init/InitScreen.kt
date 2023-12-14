package com.tonkeeper.fragment.wallet.init

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.tonkeeper.R
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.wallet.init.pager.PagerAdapter
import kotlinx.coroutines.flow.filter
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class InitScreen: BaseFragment(R.layout.fragment_pager), BaseFragment.SwipeBack {

    companion object {

        private const val CREATE_KEY = "create"

        fun newInstance(create: Boolean): InitScreen {
            val fragment = InitScreen()
            fragment.arguments = Bundle().apply {
                putBoolean(CREATE_KEY, create)
            }
            return fragment
        }
    }

    private val create: Boolean by lazy { arguments?.getBoolean(CREATE_KEY) ?: false }

    private val viewModel: InitModel by viewModels(
        factoryProducer = { InitModel.Factory(create) },
    )

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = PagerAdapter(this, viewModel.pages)

        viewModel.currentPage.launch(this) {
            pagerView.setCurrentItem(it, true)
        }

        viewModel.ready.filter { it }.launch(this) {
            navigation?.initRoot(true)
        }
    }
}