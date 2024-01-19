package com.tonapps.singer.screen.create

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.screen.create.pager.PageType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.widget.InputView
import uikit.widget.LoaderView

class CreateNameFragment: BaseFragment(R.layout.fragment_create_name) {

    companion object {
        fun newInstance() = CreateNameFragment()
    }

    private val createViewModel: CreateViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private lateinit var nameInput: InputView
    private lateinit var doneButton: Button
    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nameInput = view.findViewById(R.id.name)

        doneButton = view.findViewById(R.id.done)
        doneButton.setOnClickListener { done() }

        loaderView = view.findViewById(R.id.loader)

        nameInput.doOnTextChange = {
            doneButton.isEnabled = it.isNotBlank()
        }

        createViewModel.page(PageType.Name).onEach {
            nameInput.focus()
        }.launchIn(lifecycleScope)
    }

    private fun done() {
        val name = nameInput.text
        if (name.isBlank()) {
            return
        }

        createViewModel.setName(name)

        loaderView.visibility = View.VISIBLE
        doneButton.visibility = View.GONE
        nameInput.hideKeyboard()
    }
}