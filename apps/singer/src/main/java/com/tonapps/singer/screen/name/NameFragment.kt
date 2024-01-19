package com.tonapps.singer.screen.name

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.doOnOnApplyWindowInsets
import uikit.widget.HeaderView
import uikit.widget.InputView
import uikit.widget.LoaderView

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
    private lateinit var bottomView: View
    private lateinit var saveButton: Button
    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        nameInput = view.findViewById(R.id.name)
        bottomView = view.findViewById(R.id.bottom)

        saveButton = view.findViewById(R.id.save)
        saveButton.setOnClickListener { save() }

        loaderView = view.findViewById(R.id.loader)

        nameInput.doOnTextChange = {
            nameInput.error = false
            saveButton.isEnabled = !nameInput.isEmpty
        }

        view.doOnOnApplyWindowInsets {
            val bottomInset = it.getInsets(WindowInsetsCompat.Type.ime()).bottom
            bottomView.translationY -= bottomInset.toFloat()
            it
        }

        nameViewModel.name.onEach {
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
        loaderView.visibility = View.VISIBLE
        saveButton.visibility = View.VISIBLE

        nameViewModel.save(name)

        finish()
    }

    override fun onResume() {
        super.onResume()
        nameInput.focus()
    }
}