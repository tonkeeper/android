package com.tonapps.tonkeeper.ui.screen.dev

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.locale
import com.tonapps.security.Security
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.dev.list.launcher.LauncherAdapter
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.LinearLayoutManager
import com.tonapps.uikit.list.ListCell
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import uikit.widget.item.ItemSwitchView

class DevScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_dev, ScreenContext.None), BaseFragment.BottomSheet {

    override val viewModel: DevViewModel by viewModel()

    private lateinit var iconsView: RecyclerView
    private lateinit var blurView: ItemSwitchView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { finish() }

        val deviceView = view.findViewById<AppCompatTextView>(R.id.device)
        deviceView.text = getDeviceLines().joinToString("\n")

        iconsView = view.findViewById(R.id.icons)
        iconsView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL)
        iconsView.adapter = LauncherAdapter()

        blurView = view.findViewById(R.id.blur)
        blurView.setChecked(DevSettings.blurEnabled, false)
        blurView.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.blurEnabled = isChecked
                requireContext().showToast("Restart app to apply changes")
            }
        }
    }

    private fun getDeviceLines(): List<String> {
        val list = mutableListOf<String>()
        list.add("Context locale: ${requireContext().locale.language}")
        list.add("Android version: ${android.os.Build.VERSION.RELEASE} (API level ${android.os.Build.VERSION.SDK_INT})")
        list.add("Device model: ${android.os.Build.MODEL}")
        list.add("Screen size: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        list.add("Device rooted: ${booleanToYesOrNo(Security.isDeviceRooted())}")
        list.add("Device strongbox: ${booleanToYesOrNo(Security.isSupportStrongBox(requireContext()))}")
        list.add("ADB enabled: ${booleanToYesOrNo(Security.isAdbEnabled(requireContext()))}")
        return list
    }

    private fun booleanToYesOrNo(value: Boolean): String {
        return if (value) "yes" else "no"
    }

    companion object {

        fun newInstance() = DevScreen()
    }
}