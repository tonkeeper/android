package com.tonkeeper.fragment.country

import android.os.Bundle
import android.view.View
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.country.list.CountryAdapter
import uikit.base.fragment.BaseFragment
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.HeaderView
import uikit.widget.InputView
import uikit.widget.SearchInput

class CountryScreen: UiScreen<CountryScreenState, CountryScreenEffect, CountryScreenFeature>(R.layout.fragment_country), BaseFragment.BottomSheet {

    companion object {

        private const val REQUEST_KEY = "request"

        fun newInstance(request: String): CountryScreen {
            val fragment = CountryScreen()
            fragment.arguments = Bundle().apply {
                putString(REQUEST_KEY, request)
            }
            return fragment
        }
    }

    override val feature: CountryScreenFeature by viewModels()

    private val request: String by lazy { arguments?.getString(REQUEST_KEY) ?: "" }

    private lateinit var headerView: HeaderView
    private lateinit var searchInput: SearchInput
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        searchInput = view.findViewById(R.id.search)
        searchInput.doOnTextChanged = { feature.search(it.toString()) }

        listView = view.findViewById(R.id.list)
    }

    override fun newUiState(state: CountryScreenState) {
        listView.adapter = CountryAdapter(state.items) {
            selectCountry(it.code)
        }
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        feature.load()
    }

    private fun selectCountry(code: String) {
        feature.setSelection(code)
        finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        nav()?.setFragmentResult(request)
    }
}