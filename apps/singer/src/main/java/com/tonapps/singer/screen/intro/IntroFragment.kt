package com.tonapps.singer.screen.intro

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.singer.R
import com.tonapps.singer.screen.create.CreateFragment
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation

class IntroFragment: BaseFragment(R.layout.fragment_intro) {

    companion object {
        fun newInstance() = IntroFragment()
    }

    private lateinit var newKeyButton: Button
    private lateinit var importKeyButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        newKeyButton = view.findViewById(R.id.new_key)
        newKeyButton.setOnClickListener { navigation?.add(CreateFragment.newInstance()) }

        importKeyButton = view.findViewById(R.id.import_key)
        importKeyButton.setOnClickListener { navigation?.add(CreateFragment.newInstance()) }
    }
}