package com.tonkeeper.fragment.wallet.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import com.tonkeeper.uikit.mvi.AsyncState
import com.tonkeeper.uikit.mvi.UiScreen
import com.tonkeeper.uikit.widget.LoaderView

class WalletScreen: UiScreen<WalletScreenState, WalletScreenFeature>(R.layout.fragment_wallet) {

    companion object {
        fun newInstance() = WalletScreen()
    }

    override val viewModel: WalletScreenFeature by viewModels()

    private lateinit var loaderView: LoaderView
    private lateinit var bodyView: CoordinatorLayout
    private lateinit var amountView: AppCompatTextView
    private lateinit var addressView: AppCompatTextView
    private lateinit var sendButton: View
    private lateinit var receiveButton: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderView = view.findViewById(R.id.loader)
        bodyView = view.findViewById(R.id.body)
        amountView = view.findViewById(R.id.amount)
        addressView = view.findViewById(R.id.address)

        sendButton = view.findViewById(R.id.send)
        sendButton.setOnClickListener {

        }
        receiveButton = view.findViewById(R.id.receive)
        receiveButton.setOnClickListener {

        }
    }

    override fun newUiState(state: WalletScreenState) {
        setAsyncState(state.asyncState)
        amountView.text = state.displayBalance
        addressView.text = state.shortAddress
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            loaderView.visibility = View.VISIBLE
            bodyView.visibility = View.GONE
            loaderView.resetAnimation()
        } else {
            loaderView.visibility = View.GONE
            bodyView.visibility = View.VISIBLE
            loaderView.stopAnimation()
        }
    }
}