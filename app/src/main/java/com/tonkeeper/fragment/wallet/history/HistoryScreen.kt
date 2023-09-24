package com.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import com.tonkeeper.uikit.base.fragment.BaseFragment
import com.tonkeeper.uikit.mvi.AsyncState
import com.tonkeeper.uikit.mvi.UiScreen
import com.tonkeeper.uikit.widget.LoaderView

class HistoryScreen: UiScreen<HistoryScreenState, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {
        fun newInstance() = HistoryScreen()
    }

    override val viewModel: HistoryScreenFeature by viewModels()

    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderView = view.findViewById(R.id.loader)
    }

    override fun newUiState(state: HistoryScreenState) {
        setAsyncState(state.asyncState)
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            loaderView.visibility = View.VISIBLE
            loaderView.resetAnimation()
        } else {
            loaderView.visibility = View.GONE
            loaderView.stopAnimation()
        }
    }
}