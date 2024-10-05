package com.tonapps.tonkeeper.ui.screen.token.picker

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class TokenPickerScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_token_picker, wallet), BaseFragment.BottomSheet {

    private val args: TokenPickerArgs by lazy { TokenPickerArgs(requireArguments()) }

    override val viewModel: TokenPickerViewModel by walletViewModel {
        parametersOf(args.selectedToken, args.allowedTokens)
    }

    private val adapter = Adapter { item ->
        val token = item.raw.balance.token
        returnToken(token)
    }

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var listView: RecyclerView
    private lateinit var searchContainer: View
    private lateinit var searchInput: AppCompatEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItems, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnActionClick = { finish() }
        headerDrawable = HeaderDrawable(requireContext())

        searchContainer = view.findViewById(R.id.search_container)
        searchContainer.background = headerDrawable

        searchInput = view.findViewById(R.id.search_input)
        searchInput.doOnTextChanged { text, _, _, _ -> viewModel.search(text.toString().trim()) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium))

        collectFlow(listView.topScrolled, headerDrawable::setDivider)
    }

    private fun returnToken(token: TokenEntity) {
        navigation?.setFragmentResult(args.requestKey, Bundle().apply {
            putParcelable(TOKEN, token)
        })
        finish()
    }

    override fun onDragging() {
        super.onDragging()
        searchInput.hideKeyboard()
    }

    companion object {
        const val TOKEN = "token"

        fun newInstance(
            wallet: WalletEntity,
            requestKey: String,
            selectedToken: TokenEntity,
            allowedTokens: List<String> = emptyList()
        ): TokenPickerScreen {
            val screen = TokenPickerScreen(wallet)
            screen.setArgs(TokenPickerArgs(requestKey, selectedToken, allowedTokens))
            return screen
        }
    }
}