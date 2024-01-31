package com.tonapps.signer.screen.create.child

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.R
import com.tonapps.signer.screen.create.CreateViewModel
import com.tonapps.signer.screen.create.pager.PageType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.extensions.setPaddingTop
import uikit.widget.InputView
import uikit.widget.LoaderView

class CreateNameFragment: BaseFragment(R.layout.fragment_create_name) {

    companion object {
        fun newInstance() = CreateNameFragment()
    }

    private val createViewModel: CreateViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private lateinit var contentView: View
    private lateinit var nameInput: InputView
    private lateinit var actionView: View
    private lateinit var doneButton: Button
    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.content)

        nameInput = view.findViewById(R.id.name)

        actionView = view.findViewById(R.id.action)
        actionView.pinToBottomInsets()

        doneButton = view.findViewById(R.id.done)
        doneButton.setOnClickListener { done() }

        loaderView = view.findViewById(R.id.loader)

        nameInput.doOnTextChange = {
            doneButton.isEnabled = it.isNotBlank()
        }

        createViewModel.page(PageType.Name).onEach {
            nameInput.focus()
        }.launchIn(lifecycleScope)

        createViewModel.uiTopOffset.onEach {
            contentView.setPaddingTop(it)
        }.launchIn(lifecycleScope)
    }

    private fun done() {
        val name = nameInput.text
        if (name.isBlank()) {
            return
        }

        createViewModel.setName(name)
        createViewModel.addKey(requireContext())

        loaderView.visibility = View.VISIBLE
        doneButton.visibility = View.GONE
        nameInput.hideKeyboard()
    }
}