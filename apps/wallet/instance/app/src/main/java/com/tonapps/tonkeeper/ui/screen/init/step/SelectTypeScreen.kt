package com.tonapps.tonkeeper.ui.screen.init.step

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.add.AddWalletViewModel
import com.tonapps.tonkeeper.ui.screen.add.list.Adapter
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.add.KeystoneAddScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.add.SignerAddScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.ledger.pair.PairLedgerScreen
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.SimpleRecyclerView

class SelectTypeScreen : BaseFragment(R.layout.fragment_init_select_type) {

    override val fragmentName: String = "SelectTypeScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val addWalletViewModel: AddWalletViewModel by viewModel {
        parametersOf(requireArguments().getBoolean(ARG_WITH_NEW, true))
    }

    private val adapter = Adapter { item ->
        when (item.id) {
            // These types continue inside the same Init flow, pushed as the next step.
            Item.NEW_WALLET_ID -> initViewModel.continueWith(InitArgs.Type.New)
            Item.IMPORT_WALLET_ID -> initViewModel.continueWith(InitArgs.Type.Import)
            Item.WATCH_WALLET_ID -> initViewModel.continueWith(InitArgs.Type.Watch)
            Item.TETRA_WALLET_ID -> initViewModel.continueWith(InitArgs.Type.Tetra)
            // External pairing flows (camera/bluetooth) open their own screens and spawn a fresh
            // InitScreen once the device is paired.
            Item.SIGNER_WALLET_ID -> navigation?.add(SignerAddScreen.newInstance())
            Item.LEDGER_WALLET_ID -> navigation?.add(PairLedgerScreen.newInstance())
            Item.KEYSTONE_WALLET_ID -> navigation?.add(KeystoneAddScreen.newInstance())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
        listView.addItemDecoration(object : RecyclerView.ItemDecoration() {

            private val offset = 8.dp

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
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))

        collectFlow(initViewModel.uiTopOffset) {
            // The offset lands after the first layout pass and RecyclerView keeps its scroll
            // anchor, leaving the list looking prescrolled under the header; re-pin it to top.
            listView.updatePadding(top = it)
            listView.scrollToPosition(0)
        }

        collectFlow(addWalletViewModel.uiItems, adapter::submitList)
    }

    companion object {

        private const val ARG_WITH_NEW = "with_new"

        fun newInstance(withNew: Boolean): SelectTypeScreen {
            val fragment = SelectTypeScreen()
            fragment.putBooleanArg(ARG_WITH_NEW, withNew)
            return fragment
        }
    }
}
