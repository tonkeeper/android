package com.tonapps.tonkeeper.ui.screen.collectibles

import android.os.Bundle
import android.view.View
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.list.Adapter
import com.tonapps.tonkeeper.ui.screen.collectibles.list.Item
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.drawable.BarDrawable
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.HeaderView

class CollectiblesScreen: MainScreen.Child(R.layout.fragment_main_list) {

    private val collectiblesViewModel: CollectiblesViewModel by viewModel()

    private val adapter = Adapter()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = getString(Localization.collectibles)
        headerView.setColor(requireContext().backgroundTransparentColor)

        listView = view.findViewById(R.id.list)
        listView.updatePadding(top = 0)
        listView.layoutManager = object : GridLayoutManager(context, 3) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        listView.adapter = adapter

        collectFlow(collectiblesViewModel.uiItemsFlow, adapter::submitList)
        collectFlow(collectiblesViewModel.isUpdatingFlow) { updating ->
            if (updating) {
                headerView.setSubtitle(Localization.updating)
            } else {
                headerView.setSubtitle(null)
            }
        }
    }

    override fun getRecyclerView() = listView

    override fun getHeaderDividerOwner() = headerView

    companion object {
        fun newInstance() = CollectiblesScreen()
    }
}