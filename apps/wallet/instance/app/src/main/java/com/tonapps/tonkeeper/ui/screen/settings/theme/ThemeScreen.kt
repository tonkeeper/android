package com.tonapps.tonkeeper.ui.screen.settings.theme

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView

class ThemeScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_theme, ScreenContext.None), BaseFragment.SwipeBack {

    override val viewModel: ThemeViewModel by viewModel()

    private val adapter = Adapter { item ->
        viewModel.setTheme(item.theme.resId)
    }

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    companion object {
        fun newInstance() = ThemeScreen()
    }
}