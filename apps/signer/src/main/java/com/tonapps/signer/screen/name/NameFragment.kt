package com.tonapps.signer.screen.name

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.Key
import com.tonapps.signer.R
import com.tonapps.signer.core.repository.KeyRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.widget.HeaderView
import uikit.widget.InputView

class NameFragment: BaseFragment(R.layout.fragment_name), BaseFragment.BottomSheet {

    companion object {

        fun newInstance(id: Long): NameFragment {
            val fragment = NameFragment()
            fragment.arguments = Bundle().apply {
                putLong(Key.ID, id)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(Key.ID) }
    private val keyRepository: KeyRepository by inject()

    private lateinit var headerView: HeaderView
    private lateinit var nameInput: InputView
    private lateinit var saveButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        nameInput = view.findViewById(R.id.name)

        saveButton = view.findViewById(R.id.save)
        saveButton.setOnClickListener { save() }
        saveButton.pinToBottomInsets()

        nameInput.doOnTextChange = {
            nameInput.error = false
            saveButton.isEnabled = !nameInput.isEmpty
        }

        keyRepository.getKey(id).filterNotNull().onEach {
            nameInput.text = it.name
        }.launchIn(lifecycleScope)
    }

    private fun save() {
        val name = nameInput.text
        if (name.isEmpty()) {
            nameInput.error = true
            return
        }

        nameInput.hideKeyboard()

        lifecycleScope.launch {
            keyRepository.setName(id, name)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        nameInput.focus()
    }
}