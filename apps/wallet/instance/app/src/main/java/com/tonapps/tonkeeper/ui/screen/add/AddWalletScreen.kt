package com.tonapps.tonkeeper.ui.screen.add

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.add.list.Adapter
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.add.KeystoneAddScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.add.SignerAddScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.ledger.pair.PairLedgerScreen
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize

class AddWalletScreen: BaseListWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.Modal {

    override val viewModel: AddWalletViewModel by viewModel {
        parametersOf(requireArguments().getBoolean(ARG_WITH_NEW, false))
    }

    private val adapter = Adapter { item ->
        when(item.id) {
            Item.NEW_WALLET_ID -> openScreen(InitScreen.newInstance(InitArgs.Type.New))
            Item.IMPORT_WALLET_ID -> openScreen(InitScreen.newInstance(InitArgs.Type.Import))
            Item.WATCH_WALLET_ID -> openScreen(InitScreen.newInstance(InitArgs.Type.Watch))
            Item.TESTNET_WALLET_ID -> openScreen(InitScreen.newInstance(InitArgs.Type.Testnet))
            Item.SIGNER_WALLET_ID -> openScreen(SignerAddScreen.newInstance())
            Item.LEDGER_WALLET_ID -> openScreen(PairLedgerScreen.newInstance())
            Item.KEYSTONE_WALLET_ID -> openScreen(KeystoneAddScreen.newInstance())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItems, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.maxHeight = 680.dp
        listView.adapter = adapter
        listView.addItemDecoration(object : RecyclerView.ItemDecoration() {

            private val offset = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                val position = parent.getChildAdapterPosition(view)
                if (position == 0) {
                    return
                }
                outRect.top = offset
            }
        })

        listView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            val margin = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
            marginStart = margin
            marginEnd = margin
        }
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))
    }

    private fun openScreen(screen: BaseFragment) {
        navigation?.add(screen)
        finish()
    }

    companion object {

        private const val ARG_WITH_NEW = "with_new"

        fun newInstance(withNew: Boolean): AddWalletScreen {
            val fragment = AddWalletScreen()
            fragment.putBooleanArg(ARG_WITH_NEW, withNew)
            return fragment
        }
    }
}