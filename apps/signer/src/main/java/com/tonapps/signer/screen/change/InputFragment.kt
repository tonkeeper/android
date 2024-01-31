package com.tonapps.signer.screen.change

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.R
import com.tonapps.signer.SimpleState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.widget.password.PasswordInputView

class InputFragment: BaseFragment(R.layout.view_change_input) {

    companion object {
        private const val PAGE_INDEX_KEY = "page_index"

        fun newInstance(pageIndex: Int): InputFragment {
            val fragment = InputFragment()
            fragment.arguments = Bundle().apply {
                putInt(PAGE_INDEX_KEY, pageIndex)
            }
            return fragment
        }
    }

    private val pageIndex: Int by lazy { requireArguments().getInt(PAGE_INDEX_KEY) }

    private val changeViewModel: ChangeViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private lateinit var titleView: AppCompatTextView
    private lateinit var passwordInput: PasswordInputView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.title)
        applyTitle()

        passwordInput = view.findViewById(R.id.password)
        passwordInput.doAfterValueChanged {
            changeViewModel.setPassword(pageIndex, it)
            if (it.isNotEmpty()) {
                passwordInput.error = false
            }
        }

        changeViewModel.uiPageIndex.filter { pageIndex == it }.onEach {
            passwordInput.focusWithKeyboard()
            passwordInput.error = false
        }.launchIn(lifecycleScope)

        changeViewModel.uiState
            .onEach(::newUiState)
            .launchIn(lifecycleScope)
    }

    private fun newUiState(state: UiState) {
        if (state is UiState.Task) {
            applyState(state.state)
        }
    }

    private fun applyState(state: SimpleState) {
        if (state == SimpleState.Loading) {
            applyLoadingState()
        } else if (state == SimpleState.Error) {
            applyErrorState()
        }
    }

    private fun applyLoadingState() {
        passwordInput.hideKeyboard()
        passwordInput.isEnabled = false
    }

    private fun applyErrorState() {
        passwordInput.isEnabled = true
        passwordInput.failedPassword()
        passwordInput.focusWithKeyboard()
    }

    private fun applyTitle() {
        val titleRes = when(pageIndex) {
            ChangeViewModel.CURRENT_INDEX -> R.string.enter_current_password
            ChangeViewModel.NEW_INDEX -> R.string.enter_new_password
            ChangeViewModel.CONFIRM_INDEX -> R.string.re_enter_password
            else -> throw IllegalStateException("Unknown page index $pageIndex")
        }
        titleView.setText(titleRes)
    }
}