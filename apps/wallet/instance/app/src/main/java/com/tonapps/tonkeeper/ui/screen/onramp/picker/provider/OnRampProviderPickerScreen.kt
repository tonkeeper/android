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
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.extensions.applyNavBottomMargin
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.widget.ModalHeader

class OnRampProviderPickerScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_onramp_provider_picker, ScreenContext.Wallet(wallet)), BaseFragment.Modal {

    private val provider: PurchaseMethodEntity by lazy {
        arguments?.getParcelableCompat<PurchaseMethodEntity>(PROVIDER)!!
    }

    private val providers: List<PurchaseMethodEntity> by lazy {
        arguments?.getParcelableArrayList(PROVIDERS) ?: emptyList()
    }

    override val viewModel: OnRampProviderPickerViewModel by walletViewModel {
        parametersOf(provider, providers)
    }

    private val adapter = Adapter { item ->
        viewModel.setSelectedProvider(item.provider)
        setResult(item.provider)
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
        button.setOnClickListener { setResult(provider) }
        button.applyNavBottomMargin(16.dp)
    }

    private fun setResult(provider: PurchaseMethodEntity) {
        setResult(Bundle().apply {
            putParcelable(PROVIDER, provider)
        })
    }

    companion object {

        private const val PROVIDER = "provider"
        private const val PROVIDERS = "providers"

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            provider: PurchaseMethodEntity,
            supportedProviders: List<PurchaseMethodEntity>
        ): PurchaseMethodEntity {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val fragment = OnRampProviderPickerScreen(wallet).apply {
                putParcelableArg(PROVIDER, provider)
                putParcelableArrayListArg(PROVIDERS, ArrayList(supportedProviders))
            }
            val result = activity.addForResult(fragment)
            return result.getParcelableCompat<PurchaseMethodEntity>(PROVIDER) ?: provider
        }

    }
}