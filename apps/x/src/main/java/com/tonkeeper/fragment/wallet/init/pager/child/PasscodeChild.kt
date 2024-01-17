package com.tonkeeper.fragment.wallet.init.pager.child

import androidx.fragment.app.viewModels
import com.tonkeeper.fragment.passcode.create.PasscodeCreateScreen
import com.tonkeeper.fragment.wallet.init.InitModel

class PasscodeChild: PasscodeCreateScreen() {

    companion object {
        fun newInstance() = PasscodeChild()
    }

    private val parentFeature: InitModel by viewModels({ requireParentFragment() })

    override fun onPasscodeComplete(code: String) {
        super.onPasscodeComplete(code)
        parentFeature.setPasscode(code)
    }

}