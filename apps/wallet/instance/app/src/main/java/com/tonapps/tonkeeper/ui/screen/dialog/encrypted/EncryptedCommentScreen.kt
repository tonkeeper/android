package com.tonapps.tonkeeper.ui.screen.dialog.encrypted

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.widget.CheckBoxView
import uikit.widget.HeaderView

class EncryptedCommentScreen: BaseFragment(R.layout.fragment_encrypted_comment), BaseFragment.Modal {

    private var onAction: (() -> Unit)? = null

    private val settingsRepository: SettingsRepository by inject()

    private lateinit var headerView: HeaderView
    private lateinit var button: Button
    private lateinit var checkboxContainerView: View
    private lateinit var checkboxView: CheckBoxView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        button = view.findViewById(R.id.decrypted_button)
        button.setOnClickListener {
            if (checkboxView.checked) {
                settingsRepository.showEncryptedCommentModal = false
            }
            onAction?.invoke()
            finish()
        }

        checkboxContainerView = view.findViewById(R.id.checkbox_container)
        checkboxView = view.findViewById(R.id.checkbox)

        checkboxContainerView.setOnClickListener { checkboxView.toggle() }
    }

    companion object {
        fun newInstance(onAction: () -> Unit): EncryptedCommentScreen {
            val fragment = EncryptedCommentScreen()
            fragment.onAction = onAction
            return fragment
        }
    }
}