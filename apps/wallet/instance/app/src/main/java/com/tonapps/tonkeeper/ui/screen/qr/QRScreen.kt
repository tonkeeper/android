package com.tonapps.tonkeeper.ui.screen.qr

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment

class QRScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.BottomSheet {

    override val fragmentName: String = "QRScreen"

    override val viewModel: QRViewModel by walletViewModel {
        parametersOf(arguments?.getParcelableCompat(ARG_TOKEN) ?: TokenEntity.TON)
    }

    private val hasToken: Boolean
        get() = arguments?.getBoolean(ARG_HAS_TOKEN) ?: false

    private val enableTronDialog: EnableTronDialog by lazy {
        EnableTronDialog(this, wallet, onEnable = viewModel::enableTron)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.simpleTrackEvent("receive_open", viewModel.installId)
    }

    private fun getQrContent(address: String, token: TokenEntity): String {
        if (token.isTrc20) {
            return address
        }

        var value = "ton://transfer/${address}"
        if (!token.isTon) {
            value += "?jetton=${
                token.address.toUserFriendly(
                    wallet = false,
                    testnet = wallet.type == Wallet.Type.Testnet
                )
            }"
        }
        return value
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, viewModel.address)
        startActivity(Intent.createChooser(intent, getString(Localization.share)))
    }

    private fun copy() {
        val color = when (wallet.type) {
            Wallet.Type.Watch, Wallet.Type.Testnet -> requireContext().accentOrangeColor
            else -> requireContext().backgroundContentTintColor
        }
        navigation?.toast(getString(Localization.copied), color)
        context?.copyToClipboard(viewModel.address)
    }

    @Composable
    override fun ScreenContent() {
        val isTronDisabled = remoteConfig?.isTronDisabled ?: false
        val tabsVisible = !hasToken && wallet.hasPrivateKey && !wallet.testnet && !isTronDisabled
        val qrContent by remember {
            derivedStateOf {
                if (viewModel.address.isNotEmpty()) {
                    getQrContent(viewModel.address, viewModel.token)
                } else {
                    null
                }
            }
        }
        QrComposable(
            wallet = wallet,
            tabsVisible = tabsVisible,
            token = viewModel.token,
            address = viewModel.address,
            qrContent = qrContent,
            showBlockchain = viewModel.tronUsdtEnabled,
            onFinishClick = { finish() },
            onShareClick = { share() },
            onCopyClick = { copy() },
            onTabClick = {
                when (it) {
                    QRViewModel.Tab.TON -> viewModel.setTon()
                    QRViewModel.Tab.TRON -> {
                        if (!viewModel.tronUsdtEnabled) {
                            if (!enableTronDialog.isShowing) {
                                enableTronDialog.show()
                            }
                        } else {
                            viewModel.setTron()
                        }
                    }
                }
            }
        )
    }

    companion object {

        private const val ARG_TOKEN = "token"
        private const val ARG_HAS_TOKEN = "has_token"

        fun newInstance(
            wallet: WalletEntity,
            token: TokenEntity? = null
        ): BaseFragment {
            val screen = QRScreen(wallet)
            screen.putParcelableArg(ARG_TOKEN, token ?: TokenEntity.TON)
            screen.putBooleanArg(ARG_HAS_TOKEN, token != null)
            return screen
        }
    }
}
