package com.tonapps.singer.screen.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.singer.R
import com.tonapps.singer.screen.main.list.MainAdapter
import com.tonapps.singer.screen.root.RootViewModel
import com.tonapps.singer.screen.root.RootMode
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.list.LinearLayoutManager
import uikit.widget.HeaderView

class MainFragment: BaseFragment(R.layout.fragment_main) {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val mainViewModel: MainViewModel by viewModel()
    private val rootViewModel: RootViewModel by lazy {
        requireActivity().getViewModel()
    }

    private val adapter = MainAdapter { rootViewModel.openKey(it) }

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        listView.adapter = adapter

        mainViewModel.uiItems.onEach {
            adapter.submitList(it)
        }.launchIn(lifecycleScope)

        rootViewModel.modeFlow.onEach {
            updateMode(it)
        }.launchIn(lifecycleScope)
    }

    private fun updateMode(mode: RootMode) {
        headerView.title = if (mode is RootMode.Default) {
            getString(R.string.app_name)
        } else {
            getString(R.string.select_key)
        }
    }
}