package com.tonapps.tonkeeper.ui.screen.swap.picker

import android.content.Context
import android.os.Bundle
import com.tonapps.log.L
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.swap.picker.list.Adapter
import com.tonapps.tonkeeper.ui.screen.swap.picker.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.extensions.collectFlow
import uikit.extensions.smartScrollTo
import uikit.widget.LoaderView
import uikit.widget.ModalHeader
import uikit.widget.SearchInput
import kotlin.coroutines.cancellation.CancellationException

class SwapPickerScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_swap_picker, wallet), BaseFragment.BottomSheet {

    private val args: SwapPickerArgs by lazy { SwapPickerArgs(requireArguments()) }

    override val viewModel: SwapPickerViewModel by walletViewModel {
        parametersOf(args)
    }

    private lateinit var listView: RecyclerView
    private lateinit var loaderView: LoaderView
    private lateinit var searchInput: SearchInput
    private lateinit var emptyView: View

    private val adapter = Adapter {
        if (it is Item.Token) {
            onSelectedToken(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED), ::setUiItems)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.setTitle(if (args.send) Localization.send else Localization.receive)
        headerView.onCloseClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        loaderView = view.findViewById(R.id.loader)
        loaderView.startAnimation()

        emptyView = view.findViewById(R.id.empty)

        searchInput = view.findViewById(R.id.search)
        searchInput.doOnTextChanged = {
            viewModel.setSearchQuery(it.toString())
        }
    }

    private fun setUiItems(list: List<Item>) {
        if (list.isEmpty()) {
            loaderView.visibility = View.GONE
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.submitList(list) {
                loaderView.visibility = View.GONE
                listView.smartScrollTo(adapter.selectedIndex)
            }
        }
    }

    private fun onSelectedToken(item: Item.Token) {
        setResult(Bundle().apply {
            putParcelable(ARG_CURRENCY, item.currency)
        })
    }

    companion object {

        private const val ARG_CURRENCY = "currency"

        private fun newInstance(
            wallet: WalletEntity,
            selectedCurrency: WalletCurrency,
            ignoreCurrency: WalletCurrency,
            send: Boolean
        ): SwapPickerScreen {
            val args = SwapPickerArgs(selectedCurrency, ignoreCurrency, send)
            val screen = SwapPickerScreen(wallet)
            screen.setArgs(args)
            return screen
        }

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            selectedCurrency: WalletCurrency,
            ignoreCurrency: WalletCurrency,
            send: Boolean
        ): WalletCurrency {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val screen = newInstance(wallet, selectedCurrency, ignoreCurrency, send)
            val result = activity.addForResult(screen)
            return result.getParcelableCompat<WalletCurrency>(ARG_CURRENCY) ?: throw CancellationException()
        }
    }
}