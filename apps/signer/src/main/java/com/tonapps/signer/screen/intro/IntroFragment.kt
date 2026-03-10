package com.tonapps.signer.screen.intro

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tonapps.signer.R
import com.tonapps.signer.screen.create.CreateFragment
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class IntroFragment: BaseFragment(R.layout.fragment_intro) {

    companion object {
        fun newInstance() = IntroFragment()
    }

    private lateinit var contentView: View
    private lateinit var newKeyButton: Button
    private lateinit var importKeyButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.intro_content)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val insetsNav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            contentView.setPadding(0, 0, 0, insetsNav)
            insets
        }

        newKeyButton = view.findViewById(R.id.new_key)
        newKeyButton.setOnClickListener { openCreateFragment(false) }

        importKeyButton = view.findViewById(R.id.import_key)
        importKeyButton.setOnClickListener { openCreateFragment(true) }
    }

    private fun openCreateFragment(import: Boolean) {
        navigation?.add(CreateFragment.newInstance(import))
    }
}