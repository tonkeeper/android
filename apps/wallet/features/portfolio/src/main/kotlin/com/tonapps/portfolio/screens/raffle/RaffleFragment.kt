package com.tonapps.portfolio.screens.raffle

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.core.ComposableFragment
import com.tonapps.core.deeplink.withRaffleSourceWalletId
import com.tonapps.core.navigation.NavigationDelegate
import uikit.base.BaseFragment
import uikit.extensions.activity

class RaffleFragment : ComposableFragment(), BaseFragment.Modal {

    override val fragmentName: String = "RaffleFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Full-height content: the sheet starts right below the status bar.
        // This can't move into Compose: the margin belongs to the view-level
        // sheet container hosting the ComposeView.
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            bottomSheetView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBar
            }
            insets
        }

        val walletId = requireArguments().getString(ARG_WALLET_ID) ?: return finish()
        val raffleId = requireArguments().getString(ARG_RAFFLE_ID)
        val sourceKey = requireArguments().getString(ARG_SOURCE)
        val source = MysteryRaffleSource.entries.firstOrNull { it.key == sourceKey }
            ?: MysteryRaffleSource.DeepLink

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            RaffleScreen(
                walletId = walletId,
                raffleId = raffleId,
                source = source,
                onClose = { finish() },
                onOpenDeeplink = { deeplink ->
                    delegate?.onProcessDeeplink(deeplink.withRaffleSourceWalletId(walletId))
                },
                onOpenLink = { url ->
                    delegate?.onOpenLink(url)
                },
            )
        }
    }

    companion object {

        private const val ARG_WALLET_ID = "wallet_id"
        private const val ARG_RAFFLE_ID = "raffle_id"
        private const val ARG_SOURCE = "source"

        fun newInstance(
            walletId: String,
            raffleId: String? = null,
            source: String? = null,
        ): RaffleFragment {
            val fragment = RaffleFragment()
            fragment.putStringArg(ARG_WALLET_ID, walletId)
            fragment.putStringArg(ARG_RAFFLE_ID, raffleId)
            fragment.putStringArg(ARG_SOURCE, source)
            return fragment
        }
    }
}
