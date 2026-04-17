package com.tonapps.tonkeeper.ui.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.WindowInsetsControllerCompat
import com.tonapps.core.flags.RemoteConfig
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.tonkeeper.koin.analytics
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.koin.serverConfig
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.wallet.api.entity.ConfigEntity
import uikit.base.BaseFragment
import uikit.navigation.Navigation

abstract class BaseWalletScreen<C: ScreenContext>(
    @LayoutRes layoutId: Int,
    screenContext: C
): BaseFragment(layoutId), BaseWalletVM.Holder {

    private companion object {
        private const val ARG_SCREEN_CONTEXT = "_screen_context"
    }

    val serverConfig: ConfigEntity?
        get() = context?.serverConfig

    val remoteConfig: RemoteConfig?
        get() = context?.remoteConfig

    val analytics: AnalyticsHelper?
        get() = context?.analytics

    override val uiContext: Context
        get() = requireContext()

    val rootActivity: RootActivity?
        get() = activity as? RootActivity

    @Deprecated("Use AsyncViewModel")
    abstract val viewModel: BaseWalletVM?

    private val isBottomSheet: Boolean
        get() = this is BottomSheet

    val windowInsetsController: WindowInsetsControllerCompat?
        get() = rootActivity?.windowInsetsController

    private val isAppearanceLightStatusBars: Boolean by lazy {
        windowInsetsController?.isAppearanceLightStatusBars ?: false
    }

    val navigation: Navigation?
        get() = context?.let { Navigation.from(it) }

    val screenContext: C by lazy {
        requireArguments().getParcelable(ARG_SCREEN_CONTEXT)!!
    }

    init {
        if (!(screenContext is ScreenContext.Wallet && screenContext.isEmpty)) {
            putParcelableArg(ARG_SCREEN_CONTEXT, screenContext)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (isBottomSheet && isAppearanceLightStatusBars) {
            rootActivity?.setAppearanceLight(false)
        }
        viewModel?.attachHolder(this)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBottomSheet && isAppearanceLightStatusBars) {
            rootActivity?.setAppearanceLight(true)
        }
        viewModel?.detachHolder()
    }
}
