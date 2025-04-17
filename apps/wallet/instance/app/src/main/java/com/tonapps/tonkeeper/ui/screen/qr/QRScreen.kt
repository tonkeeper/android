package com.tonapps.tonkeeper.ui.screen.qr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.compose.Dimens
import uikit.compose.UIKit
import uikit.compose.components.AsyncImage
import uikit.compose.components.Header
import uikit.compose.components.ImageShape
import uikit.compose.components.TextHeader

class QRScreen(wallet: WalletEntity): ComposeWalletScreen(wallet), BaseFragment.BottomSheet {

    override val fragmentName: String = "QRScreen"

    override val viewModel: QRViewModel by walletViewModel {
        parametersOf(arguments?.getParcelableCompat(ARG_TOKEN) ?: TokenEntity.TON)
    }

    private val hasToken: Boolean
        get() = arguments?.getBoolean(ARG_HAS_TOKEN) ?: false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.simpleTrackEvent("receive_open", viewModel.installId)
    }

    private val deeplink: String by lazy {
        var value = "ton://transfer/${wallet.address}"
        if (!token.isTon) {
            value += "?jetton=${token.address.toUserFriendly(
                wallet = false,
                testnet = wallet.type == Wallet.Type.Testnet
            )}"
        }
        value
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        if (token.isTon) {
            intent.putExtra(Intent.EXTRA_TEXT, wallet.address)
        } else {
            intent.putExtra(Intent.EXTRA_TEXT, deeplink)
        }
        startActivity(Intent.createChooser(intent, getString(Localization.share)))
    }

    private fun copy() {
        val color = when (wallet.type) {
            Wallet.Type.Watch -> requireContext().accentOrangeColor
            else -> requireContext().backgroundContentTintColor
        }
        navigation?.toast(getString(Localization.copied), color)
        context?.copyToClipboard(viewModel.address)
    }

    @Composable
    override fun ScreenContent() {
        QrComposable(
            wallet = wallet,
            token = token,
            deeplink = deeplink,
            onFinishClick = { finish() },
            onShareClick = { share() },
            onCopyClick = { copy() }
        )
    }

    companion object {

        private const val ARG_TOKEN = "token"
        private const val ARG_HAS_TOKEN = "has_token"

        fun newInstance(
            wallet: WalletEntity,
            token: TokenEntity
        ): BaseFragment {
            val screen = QRScreen(wallet)
            screen.putParcelableArg(ARG_TOKEN, token ?: TokenEntity.TON)
            screen.putBooleanArg(ARG_HAS_TOKEN, token != null)
            return screen
        }
    }
}
