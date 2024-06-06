package com.tonapps.tonkeeper.ui.screen.token.picker

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation

class TokenPickerScreen: BaseFragment(R.layout.fragment_token_picker), BaseFragment.BottomSheet {

    private val requestKey: String by lazy { requireArguments().getString(ARG_REQUEST_KEY)!! }
    private val tokenPickerViewModel: TokenPickerViewModel by viewModel()

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

        tokenPickerViewModel.setSelectedToken(requireArguments().getParcelableCompat<TokenEntity>(ARG_SELECTED_TOKEN)!!)
        collectFlow(tokenPickerViewModel.uiItems, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerDrawable = HeaderDrawable(requireContext())

        searchContainer = view.findViewById(R.id.search_container)
        searchContainer.background = headerDrawable

        searchInput = view.findViewById(R.id.search_input)
        searchInput.doOnTextChanged { text, _, _, _ -> tokenPickerViewModel.search(text.toString().trim()) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium))

        collectFlow(listView.topScrolled, headerDrawable::setDivider)
    }

    private fun returnToken(token: TokenEntity) {
        tokenPickerViewModel.setSelectedToken(token)
        navigation?.setFragmentResult(requestKey, Bundle().apply {
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

        private const val ARG_SELECTED_TOKEN = "selected_token"
        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(
            requestKey: String,
            selectedToken: TokenEntity
        ): TokenPickerScreen {
            val screen = TokenPickerScreen()
            screen.arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
                putParcelable(ARG_SELECTED_TOKEN, selectedToken)
            }
            return screen
        }
    }
}