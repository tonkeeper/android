package com.tonapps.singer.screen.password

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.core.password.Password
import com.tonapps.singer.core.SimpleState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.extensions.scale
import uikit.widget.HeaderView
import uikit.widget.password.PasswordInputView

open class SecurityFragment(
    private val contentLayoutId: Int
): BaseFragment(R.layout.fragment_password) {

    private companion object {
        private const val animationDuration = 240L
        private const val targetScale = 2f
    }

    private val passwordViewModel: PasswordViewModel by viewModel()

    private lateinit var securityContentView: FrameLayout
    private lateinit var securityOverlayView: FrameLayout
    private lateinit var securityHeaderView: HeaderView
    private lateinit var securityPasswordInputView: PasswordInputView
    private lateinit var securityAction: View
    private lateinit var securityButton: Button
    private lateinit var securityLoader: View
    private lateinit var securitySuccessView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        securityOverlayView = view.findViewById(R.id.security_overlay)
        securityOverlayView.setOnClickListener {  }

        securityHeaderView = view.findViewById(R.id.security_header)
        securityHeaderView.doOnCloseClick = { finish() }

        securityPasswordInputView = view.findViewById(R.id.security_password)
        securityAction = view.findViewById(R.id.security_action)
        securityAction.pinToBottomInsets()

        securityButton = view.findViewById(R.id.security_button)
        securityButton.setOnClickListener { sendSecurityPassword() }

        securityLoader = view.findViewById(R.id.security_loader)
        securitySuccessView = view.findViewById(R.id.security_success)

        securityPasswordInputView.doAfterTextChanged {
            if (it.isNullOrBlank()) {
                return@doAfterTextChanged
            }
            securityPasswordInputView.error = false
            securityButton.isEnabled = Password.isValid(it.toString())
        }

        passwordViewModel.uiState.onEach(::setSecurityState).launchIn(lifecycleScope)
    }

    private fun sendSecurityPassword() {
        val password = securityPasswordInputView.text.toString()
        if (Password.isValid(password)) {
            passwordViewModel.checkPassword(password)
        } else {
            applySecurityErrorState()
        }
    }

    private fun setSecurityState(state: SimpleState) {
        when (state) {
            SimpleState.Default -> applySecurityDefaultState()
            SimpleState.Error -> applySecurityErrorState()
            SimpleState.Success -> applySecuritySuccessState()
            SimpleState.Loading -> applySecurityLoadingState()
        }
    }

    private fun applySecurityErrorState() {
        securityPasswordInputView.failedPassword()
        applySecurityDefaultState()
    }

    private fun applySecurityLoadingState() {
        securityPasswordInputView.hideKeyboard()
        securityPasswordInputView.isEnabled = false

        securityButton.visibility = View.GONE
        securityLoader.visibility = View.VISIBLE
    }

    private fun applySecuritySuccessState() {
        securityContentView.visibility = View.VISIBLE
        startHideSecurityAnimation()
    }

    private fun startHideSecurityAnimation() {
        val animator = ValueAnimator.ofFloat(1f, 0f)
        animator.duration = animationDuration
        animator.addUpdateListener {
            val progress = it.animatedValue as Float
            securityOverlayView.scale = targetScale + (1 - targetScale) * progress
            securityOverlayView.alpha = progress
        }
        animator.doOnEnd {
            securityOverlayView.visibility = View.GONE
        }
        animator.start()
    }

    private fun applySecurityDefaultState() {
        securityButton.visibility = View.VISIBLE
        securityLoader.visibility = View.GONE
        securitySuccessView.visibility = View.GONE

        securityPasswordInputView.text?.clear()
        securityPasswordInputView.focusWithKeyboard()
        securityPasswordInputView.isEnabled = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)!!
        securityContentView = view.findViewById(R.id.security_content)
        inflater.inflate(contentLayoutId, securityContentView, true)
        return view
    }

    override fun onResume() {
        super.onResume()
        if (securityOverlayView.visibility == View.VISIBLE) {
            securityPasswordInputView.focusWithKeyboard()
        }
    }
}