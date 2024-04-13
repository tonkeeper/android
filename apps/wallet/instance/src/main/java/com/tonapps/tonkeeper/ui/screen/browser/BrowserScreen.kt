package com.tonapps.tonkeeper.ui.screen.browser

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeperx.R
import uikit.widget.HeaderView

class BrowserScreen : MainScreen.Child(R.layout.fragment_browser) {

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
    }

    override fun getRecyclerView() = listView

    override fun getHeaderDividerOwner() = headerView

    companion object {
        fun newInstance() = BrowserScreen()
    }
}