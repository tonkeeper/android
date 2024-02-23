package com.tonapps.tonkeeper.ui.screen.init.pager.child

import com.tonapps.tonkeeper.fragment.passcode.create.PasscodeCreateScreen
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel

class PasscodeChild: PasscodeCreateScreen() {

    companion object {
        fun newInstance() = PasscodeChild()
    }

    private val parentFeature: InitViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    override fun onPasscodeComplete(code: String) {
        super.onPasscodeComplete(code)
        parentFeature.setPasscode(code)
    }

}