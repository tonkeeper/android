package com.tonkeeper.fragment.receive

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import uikit.mvi.UiScreen
import uikit.widget.HeaderView

class ReceiveScreen: UiScreen<ReceiveScreenState, ReceiveScreenEffect, ReceiveScreenFeature>(R.layout.fragment_receive), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = ReceiveScreen()
    }

    override val feature: ReceiveScreenFeature by viewModels()

    private lateinit var headerView: HeaderView
    private lateinit var contentView: View
    private lateinit var qrCodeView: SquareImageView
    private lateinit var addressView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        contentView = view.findViewById(R.id.content)

        qrCodeView = view.findViewById(R.id.qr_code)
        qrCodeView.doOnLayout {
            feature.requestQRCode(it.measuredWidth)
        }

        addressView = view.findViewById(R.id.address)
    }

    override fun newUiState(state: ReceiveScreenState) {
        qrCodeView.setImageBitmap(state.qrCode)

        addressView.text = state.address

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