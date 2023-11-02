package com.tonkeeper.fragment.intro

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment

class IntroFragment: BaseFragment(R.layout.fragment_intro) {

    companion object {
        fun newInstance() = IntroFragment()
    }

    private lateinit var titleView: AppCompatTextView
    private lateinit var startButton: Button

    private val bottomSheet: IntroWalletDialog by lazy {
        IntroWalletDialog(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.title)
        titleView.text = getSpannable(R.string.intro_title)

        startButton = view.findViewById(R.id.start)
        startButton.setOnClickListener {
            bottomSheet.show()
        }
    }

}