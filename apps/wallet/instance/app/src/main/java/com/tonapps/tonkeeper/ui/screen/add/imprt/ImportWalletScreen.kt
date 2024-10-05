package com.tonapps.tonkeeper.ui.screen.add.imprt

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.add.imprt.list.Adapter
import com.tonapps.tonkeeper.ui.screen.add.imprt.list.Item
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.add.KeystoneAddScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.add.SignerAddScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.ledger.pair.PairLedgerScreen
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class ImportWalletScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_import_wallet, ScreenContext.None), BaseFragment.Modal {

    override val viewModel: ImportWalletViewModel by viewModel()

    private val adapter = Adapter { item ->
        when(item.id) {
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
        val header = view.findViewById<HeaderView>(R.id.header)
        header.doOnActionClick = { finish() }

        val listView = view.findViewById<SimpleRecyclerView>(R.id.list)
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
    }

    private fun openScreen(screen: BaseFragment) {
        navigation?.add(screen)
        finish()
    }

    companion object {

        fun newInstance() = ImportWalletScreen()
    }
}