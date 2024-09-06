package com.tonapps.tonkeeper.ui.screen.dev

import android.os.Bundle
import android.view.View
import com.tonapps.extensions.locale
import com.tonapps.security.Security
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class DevScreen: BaseWalletScreen(R.layout.fragment_dev), BaseFragment.BottomSheet {

    override val viewModel: DevViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { finish() }

        setData(view, R.id.locale, requireContext().locale.language, ListCell.Position.FIRST)
        setData(view, R.id.android_version, "${android.os.Build.VERSION.RELEASE} (API level ${android.os.Build.VERSION.SDK_INT})")
        setData(view, R.id.device_model, android.os.Build.MODEL)
        setData(view, R.id.screen_size, "${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        setData(view, R.id.device_root, Security.isDeviceRooted())
        setData(view, R.id.device_strongbox, Security.isSupportStrongBox(requireContext()))
        setData(view, R.id.device_adb, Security.isAdbEnabled(requireContext()), ListCell.Position.LAST)
    }

    private fun setData(
        view: View,
        id: Int,
        data: String,
        position: ListCell.Position = ListCell.Position.MIDDLE
    ) {
        val itemView = view.findViewById<TransactionDetailView>(id)
        itemView.position = position
        itemView.setData(data, null)
    }

    private fun setData(
        view: View,
        id: Int,
        data: Boolean,
        position: ListCell.Position = ListCell.Position.MIDDLE
    ) {
        setData(view, id, booleanToYesOrNo(data), position)
    }

    private fun booleanToYesOrNo(value: Boolean): String {
        return if (value) "yes" else "no"
    }

    companion object {

        fun newInstance() = DevScreen()
    }
}