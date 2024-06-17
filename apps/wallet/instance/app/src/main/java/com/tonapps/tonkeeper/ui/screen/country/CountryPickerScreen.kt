package com.tonapps.tonkeeper.ui.screen.country

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.country.list.Adapter
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.topScrolled
import uikit.widget.HeaderView
import uikit.widget.SearchInput

class CountryPickerScreen: BaseFragment(R.layout.fragment_country), BaseFragment.BottomSheet {

    private val requestKey: String by lazy { requireArguments().getString(ARG_REQUEST_KEY)!! }
    private val countryPickerViewModel: CountryPickerViewModel by viewModel()
    private val adapter = Adapter(::setCountry)

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var resultContract: CountryResultContract
    private lateinit var headerView: HeaderView
    private lateinit var searchView: SearchInput
    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultContract = CountryResultContract(requestKey, requireContext())
        collectFlow(countryPickerViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerDrawable = HeaderDrawable(requireContext())

        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        searchView = view.findViewById(R.id.search)
        searchView.background = headerDrawable
        searchView.doOnTextChanged = countryPickerViewModel::search

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium))

        collectFlow(listView.topScrolled, headerDrawable::setDivider)
    }

    private fun setCountry(code: String) {
        countryPickerViewModel.setCountry(code)
        resultContract.setResult(code)
        finish()
    }

    companion object {
        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(requestKey: String): CountryPickerScreen {
            val fragment = CountryPickerScreen()
            fragment.arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
            }
            return fragment
        }
    }
}