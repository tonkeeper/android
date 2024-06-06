package com.tonapps.signer.screen.name

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.Key
import com.tonapps.signer.R
import com.tonapps.signer.core.repository.KeyRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.extensions.doKeyboardAnimation
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
        nameInput.setOnDoneActionListener {save() }

        saveButton = view.findViewById(R.id.save)
        saveButton.setOnClickListener { save() }

        nameInput.doOnTextChange = {
            nameInput.error = false
            saveButton.isEnabled = !nameInput.isEmpty
        }

        keyRepository.getKey(id).filterNotNull().onEach {
            nameInput.text = it.name
        }.launchIn(lifecycleScope)

        view.doKeyboardAnimation { offset, _, _ ->
            saveButton.translationY = -offset.toFloat()
        }
    }

    private fun save() {
        val name = nameInput.text.trim()
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