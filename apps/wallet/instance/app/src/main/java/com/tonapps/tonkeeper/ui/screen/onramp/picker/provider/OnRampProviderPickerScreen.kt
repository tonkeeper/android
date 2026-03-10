package com.tonapps.tonkeeper.ui.screen.onramp.picker.provider

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.UiState
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.extensions.applyNavBottomMargin
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.widget.ModalHeader

class OnRampProviderPickerScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_onramp_provider_picker, ScreenContext.Wallet(wallet)), BaseFragment.Modal {

    private val state: UiState.SelectedProvider by lazy {
        arguments?.getParcelableCompat<UiState.SelectedProvider>(ARG_STATE)!!
    }

    override val viewModel: OnRampProviderPickerViewModel by walletViewModel {
        parametersOf(state)
    }

    private val adapter = Adapter { item ->
        viewModel.setSelectedProvider(item.id)
        setResult(item.id)
    }

    private lateinit var headerView: ModalHeader
    private lateinit var listView: RecyclerView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.onCloseClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        button = view.findViewById(R.id.button)
        button.setOnClickListener { setResult(state.selectedProviderId) }
        button.applyNavBottomMargin(16.dp)
    }

    private fun setResult(providerId: String?) {
        setResult(Bundle().apply {
            putString(ARG_PROVIDER_ID, providerId)
        })
    }

    companion object {

        private const val ARG_STATE = "state"
        private const val ARG_PROVIDER_ID = "provider_id"

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            state: UiState.SelectedProvider
        ): String? {
            if (state.providers.isEmpty()) {
                throw IllegalArgumentException("No providers available")
            }
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val fragment = OnRampProviderPickerScreen(wallet).apply {
                putParcelableArg(ARG_STATE, state)
            }
            val result = activity.addForResult(fragment)
            return result.getString(ARG_PROVIDER_ID)
        }

    }
}