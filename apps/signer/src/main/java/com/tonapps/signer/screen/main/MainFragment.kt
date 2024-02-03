package com.tonapps.signer.screen.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.R
import com.tonapps.signer.screen.key.KeyFragment
import com.tonapps.signer.screen.main.list.MainAdapter
import com.tonapps.signer.screen.root.RootViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.verticalScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class MainFragment: BaseFragment(R.layout.fragment_main) {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val mainViewModel: MainViewModel by viewModel()

    private val adapter = MainAdapter {
        navigation?.add(KeyFragment.newInstance(it))
    }

    private lateinit var headerView: HeaderView
    private lateinit var listView: SimpleRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(listView.verticalScrolled) {
            headerView.divider = it
        }

        collectFlow(mainViewModel.uiItems, adapter::submitList)
    }
}