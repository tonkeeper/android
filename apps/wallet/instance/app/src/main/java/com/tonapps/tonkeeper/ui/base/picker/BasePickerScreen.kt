package com.tonapps.tonkeeper.ui.base.picker

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.R
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.getDimensionPixelSize

abstract class BasePickerScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_base_picker, ScreenContext.None) {

    abstract val adapter: RecyclerView.Adapter<*>

    private lateinit var listView: RecyclerView

    private lateinit var emptyView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium))

        emptyView = view.findViewById(R.id.empty)
    }

    fun setEmptyVisibility(visible: Boolean) {
        emptyView.visibility = if (visible) View.VISIBLE else View.GONE
    }
}