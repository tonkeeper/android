package com.tonkeeper.fragment.receive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.viewModels
import com.tonkeeper.Global
import com.tonkeeper.R
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.toJSON
import com.tonkeeper.extensions.copyToClipboard
import io.tonapi.models.JettonBalance
import uikit.base.BaseFragment
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.HeaderView

class ReceiveScreen: UiScreen<ReceiveScreenState, ReceiveScreenEffect, ReceiveScreenFeature>(R.layout.fragment_receive), BaseFragment.BottomSheet {

    companion object {

        private const val JETTON_KEY = "jetton"

        fun newInstance(jetton: JettonBalance? = null): ReceiveScreen {
            val fragment = ReceiveScreen()
            fragment.arguments = Bundle().apply {
                putString(JETTON_KEY, toJSON(jetton))
            }
            return fragment
        }
    }

    override val feature: ReceiveScreenFeature by viewModels()

    private val jetton: JettonBalance? by lazy {
        val value = arguments?.getString(JETTON_KEY)
        if (value.isNullOrBlank()) {
            null
        } else {
            fromJSON(value)
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var contentView: View
    private lateinit var receiveTitleView: AppCompatTextView
    private lateinit var receiveDescriptionView: AppCompatTextView
    private lateinit var qrCodeView: SquareImageView
    private lateinit var addressView: AppCompatTextView
    private lateinit var copyView: View
    private lateinit var shareView: View
    private lateinit var iconView: FrescoView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        contentView = view.findViewById(R.id.content)
        receiveTitleView = view.findViewById(R.id.receive_title)

        receiveDescriptionView = view.findViewById(R.id.receive_description)

        qrCodeView = view.findViewById(R.id.qr_code)
        qrCodeView.doOnLayout {
            feature.requestQRCode(it.measuredWidth, jetton)
        }

        addressView = view.findViewById(R.id.address)
        addressView.setOnClickListener { feature.copy() }

        copyView = view.findViewById(R.id.copy)
        copyView.setOnClickListener { feature.copy() }

        shareView = view.findViewById(R.id.share)
        shareView.setOnClickListener { feature.share() }

        iconView = view.findViewById(R.id.icon)

        val iconUrl = jetton?.jetton?.image ?: Global.tonCoinUrl
        iconView.setImageURI(iconUrl)

        val title = jetton?.jetton?.name ?: getString(R.string.toncoin)

        receiveTitleView.text = getString(R.string.receive_coin, title)
        receiveDescriptionView.text = getString(R.string.receive_coin_description, title)
    }

    private fun shareAddress(address: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, address)
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun copyAddress(address: String) {
        navigation?.toast(getString(R.string.copied))
        context?.copyToClipboard(address)
    }

    override fun newUiState(state: ReceiveScreenState) {
        qrCodeView.setImageBitmap(state.qrCode)

        addressView.text = state.address
    }

    override fun newUiEffect(effect: ReceiveScreenEffect) {
        super.newUiEffect(effect)
        if (effect is ReceiveScreenEffect.Share) {
            shareAddress(effect.address)
        } else if (effect is ReceiveScreenEffect.Copy) {
            copyAddress(effect.address)
        }
    }

    class SquareImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
    ) : AppCompatImageView(context, attrs, defStyle) {

        init {
            scaleType = ScaleType.CENTER_CROP
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }
    }
}