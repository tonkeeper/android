package com.tonapps.singer.screen.create

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.singer.R
import com.tonapps.singer.screen.create.pager.PagerAdapter
import com.tonapps.singer.screen.root.RootViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class CreateFragment: BaseFragment(R.layout.fragment_create) {

    companion object {

        private const val IMPORT_KEY = "new"

        fun newInstance(import: Boolean): CreateFragment {
            val fragment = CreateFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(IMPORT_KEY, import)
            }
            return fragment
        }
    }

    private val import: Boolean by lazy { requireArguments().getBoolean(IMPORT_KEY) }

    private val createViewModel: CreateViewModel by viewModel { parametersOf(import) }

    private lateinit var headerView: HeaderView
    private lateinit var adapter: PagerAdapter
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = {
            if (!createViewModel.prev()) {
                finish()
            }
        }

        adapter = PagerAdapter(this, createViewModel.pages)

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.offscreenPageLimit = adapter.itemCount
        pagerView.adapter = adapter

        createViewModel.pageIndex().onEach {
            pagerView.currentItem = it
        }.launchIn(lifecycleScope)

        createViewModel.onReady.onEach {
            finish()
        }.launchIn(lifecycleScope)
    }
}