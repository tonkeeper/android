package com.tonkeeper.fragment.passcode

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import com.tonkeeper.widget.NumPadView
import com.tonkeeper.widget.PasscodeView

class PasscodeFragment: BaseFragment(R.layout.fragment_passcode) {

    companion object {
        fun newInstance() = PasscodeFragment()
    }

    private lateinit var titleView: TextView
    private lateinit var passcodeView: PasscodeView
    private lateinit var numPadView: NumPadView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.title)
        titleView.setText(R.string.passcode_create)

        passcodeView = view.findViewById(R.id.passcode)

        numPadView = view.findViewById(R.id.num_pad)
        numPadView.doOnNumberClick = { number ->
            passcodeView.addActive()
        }
    }

}