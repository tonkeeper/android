package com.tonapps.tonkeeper.ui.screen.dns.renew

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.extensions.locale
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.dns.renew.list.Adapter
import com.tonapps.tonkeeper.ui.screen.dns.renew.list.Item
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.pinToBottomInsets

class DNSRenewScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

    private val rootViewModel: RootViewModel by activityViewModel()

    override val viewModel: DNSRenewViewModel by walletViewModel {
        parametersOf(requireArguments().getParcelableArrayList<DnsExpiringEntity>(ARG_ITEMS)!!)
    }

    private val adapter = Adapter()

    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, ::submitList)
    }

    private fun submitList(items: List<Item>) {
        adapter.submitList(items)
        if (items.isEmpty()) {
            finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.renew_dns_title))
        setAdapter(adapter)
        setNestedScrollingEnabled(true)

        actionButton = view.findViewById<Button>(R.id.action)

        collectFlow(viewModel.showRenewAllButtonFlow, ::applyRenewAllButtonState)
    }

    private fun applyRenewAllButtonState(show: Boolean) {
        if (show) {
            setBottomMargin(72.dp)
            actionButton.visibility = View.VISIBLE
            actionButton.text = requireContext().getString(Localization.renew_dns_until, DateHelper.untilDate(
                locale = requireContext().locale
            ))
            actionButton.pinToBottomInsets()
            actionButton.setOnClickListener { renewAll() }
        } else {
            actionButton.visibility = View.GONE
            setBottomMargin(0)
        }
    }

    private fun renewAll() {
        viewModel.renewAll {
            finish()
        }
    }

    companion object {

        private const val ARG_ITEMS = "items"

        fun newInstance(wallet: WalletEntity, items: List<DnsExpiringEntity>): DNSRenewScreen {
            val screen = DNSRenewScreen(wallet)
            screen.putParcelableListArg(ARG_ITEMS, items)
            return screen
        }
    }
}