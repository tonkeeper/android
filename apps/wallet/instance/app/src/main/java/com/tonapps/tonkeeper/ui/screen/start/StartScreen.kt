package com.tonapps.tonkeeper.ui.screen.start

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.airbnb.lottie.LottieAnimationView
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.serverConfig
import com.tonapps.tonkeeper.ui.screen.dev.DevScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.navigation.Navigation.Companion.navigation

class StartScreen : BaseFragment(R.layout.fragment_intro) {

    override val fragmentName: String = "StartScreen"

    private val feature: StartFeature by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyNavBottomPadding()

        if (savedInstanceState == null) {
            AnalyticsHelper.Default.events.onboardingFlow.onboardingViewWelcome()
            feature.registerDevice()
        }

        val logoView = view.findViewById<LottieAnimationView>(R.id.logo)
        logoView.setOnClickListener {
            logoView.playAnimation()
        }
        logoView.setOnLongClickListener {
            navigation?.add(DevScreen.newInstance())
            true
        }

        val newWalletButton = view.findViewById<Button>(R.id.new_wallet)
        newWalletButton.setOnClickListener {
            navigation?.add(InitScreen.newInstance(InitArgs.Type.New))
        }

        val importWalletButton = view.findViewById<Button>(R.id.import_wallet)
        importWalletButton.setOnClickListener {
            navigation?.add(InitScreen.newInstance(InitArgs.Type.AddWallet, withNew = false))
        }

        val termsView = view.findViewById<AppCompatTextView>(R.id.terms)
        termsView.text = getSpannable(Localization.start_agree)
        termsView.setOnClickListener {
            BrowserHelper.open(
                requireContext(),
                requireContext().serverConfig?.termsOfUseUrl ?: return@setOnClickListener
            )
        }
    }

    companion object {
        fun newInstance() = StartScreen()
    }
}
