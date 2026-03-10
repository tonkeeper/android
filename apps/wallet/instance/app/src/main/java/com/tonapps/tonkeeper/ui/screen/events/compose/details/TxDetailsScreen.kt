package com.tonapps.tonkeeper.ui.screen.events.compose.details

import androidx.compose.runtime.Composable
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.tonkeeper.ui.screen.events.compose.details.ui.TxDetailsComposable
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.tx.model.TxEvent
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import kotlin.getValue

class TxDetailsScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.Modal {

    private val tx: TxEvent by lazy {
        requireArguments().getParcelableCompat(ARG_TX)!!
    }

    private val actionIndex: Int by lazy {
        requireArguments().getInt(ARG_ACTION_INDEX)
    }

    override val viewModel: TxDetailsViewModel by walletViewModel {
        parametersOf(tx, actionIndex)
    }

    @Composable
    override fun ScreenContent() {
        TxDetailsComposable(
            viewModel = viewModel,
            onCloseClick = { finish() }
        )
    }

    companion object {

        private const val ARG_TX = "tx"
        private const val ARG_ACTION_INDEX = "index"

        fun newInstance(wallet: WalletEntity, tx: TxEvent, actionIndex: Int): TxDetailsScreen {
            val screen = TxDetailsScreen(wallet)
            screen.putParcelableArg(ARG_TX, tx)
            screen.putIntArg(ARG_ACTION_INDEX, actionIndex)
            return screen
        }
    }
}