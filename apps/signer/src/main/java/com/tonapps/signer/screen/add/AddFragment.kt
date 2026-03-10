package com.tonapps.signer.screen.add

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.signer.R
import com.tonapps.signer.screen.create.CreateFragment
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class AddFragment: BaseFragment(R.layout.fragment_add_key), BaseFragment.Modal {

    companion object {
        fun newInstance() = AddFragment()
    }

    private lateinit var closeButton: View
    private lateinit var createButton: Button
    private lateinit var importButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeButton = view.findViewById(R.id.close)
        closeButton.setOnClickListener { finish() }

        createButton = view.findViewById(R.id.create)
        createButton.setOnClickListener { openCreate(false) }

        importButton = view.findViewById(R.id.imprt)
        importButton.setOnClickListener { openCreate(true) }
    }

    private fun openCreate(import: Boolean) {
        navigation?.add(CreateFragment.newInstance(import))
        finish()
    }
}