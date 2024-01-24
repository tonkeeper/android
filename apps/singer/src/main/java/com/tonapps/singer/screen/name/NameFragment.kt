package com.tonapps.singer.screen.name

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.doOnBottomInsetsChanged
import uikit.extensions.pinToBottomInsets
import uikit.widget.HeaderView
import uikit.widget.InputView

class NameFragment: BaseFragment(R.layout.fragment_name), BaseFragment.BottomSheet {

    companion object {
        private const val ID_KEY = "id"

        fun newInstance(id: Long): NameFragment {
            val fragment = NameFragment()
            fragment.arguments = Bundle().apply {
                putLong(ID_KEY, id)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(ID_KEY) }
    private val nameViewModel: NameViewModel by viewModel { parametersOf(id) }

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

        nameViewModel.nameFlow.onEach {
            nameInput.text = it
        }.launchIn(lifecycleScope)
    }

    private fun save() {
        val name = nameInput.text
        if (name.isEmpty()) {
            nameInput.error = true
            return
        }

        nameInput.hideKeyboard()
        nameViewModel.save(name)
        finish()
    }

    override fun onResume() {
        super.onResume()
        nameInput.focus()
    }
}