package com.tonapps.tonkeeper.ui.screen.qr

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.widget.FrescoView
import uikit.widget.HeaderView
import uikit.widget.TextHeaderView

class QRScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_qr, wallet), BaseFragment.BottomSheet {

    private val token: TokenEntity by lazy {
        arguments?.getParcelableCompat(ARG_TOKEN) ?: TokenEntity.TON
    }

    override val viewModel: QRViewModel by walletViewModel()

    private lateinit var headerView: HeaderView
    private lateinit var infoView: TextHeaderView
    private lateinit var qrView: QRView
    private lateinit var iconView: FrescoView
    private lateinit var addressView: AppCompatTextView
    private lateinit var walletTypeView: AppCompatTextView
    private lateinit var copyView: View
    private lateinit var shareView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        infoView = view.findViewById(R.id.info)
        infoView.title = getString(Localization.receive_coin, token.name)
        infoView.desciption = getString(Localization.receive_coin_description, token.name)

        qrView = view.findViewById(R.id.qr)
        qrView.setContent(getDeepLink())

        iconView = view.findViewById(R.id.icon)
        iconView.setImageURI(token.imageUri)

        addressView = view.findViewById(R.id.address)
        addressView.setOnClickListener { copy() }
        addressView.text = wallet.address

        walletTypeView = view.findViewById(R.id.wallet_type)
        if (wallet.type == Wallet.Type.Watch) {
            walletTypeView.visibility = View.VISIBLE
            walletTypeView.setText(Localization.watch_only)
            walletTypeView.backgroundTintList = requireContext().accentOrangeColor.stateList
        } else {
            walletTypeView.visibility = View.GONE
        }

        copyView = view.findViewById(R.id.copy)
        copyView.setOnClickListener { copy() }

        shareView = view.findViewById(R.id.share)
        shareView.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, wallet.address)
            startActivity(Intent.createChooser(intent, getString(Localization.share)))
        }
    }

    private fun copy() {
        val color = when (wallet.type) {
            Wallet.Type.Watch -> requireContext().accentOrangeColor
            else -> requireContext().backgroundContentTintColor
        }
        navigation?.toast(getString(Localization.copied), color)
        context?.copyToClipboard(wallet.address)
    }

    private fun getDeepLink(): String {
        var deepLink = "ton://transfer/${wallet.address}"
        if (!token.isTon) {
            deepLink += "?token=${token.address.toUserFriendly(
                wallet = false,
                testnet = wallet.type == Wallet.Type.Testnet
            )}"
        }
        return deepLink
    }

    companion object {

        private const val ARG_TOKEN = "token"

        fun newInstance(
            wallet: WalletEntity,
            token: TokenEntity
        ): QRScreen {
            val screen = QRScreen(wallet)
            screen.putParcelableArg(ARG_TOKEN, token)
            return screen
        }
    }
}