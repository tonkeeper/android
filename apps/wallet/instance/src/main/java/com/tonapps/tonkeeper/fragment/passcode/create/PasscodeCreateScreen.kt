package com.tonapps.tonkeeper.fragment.passcode.create

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.passcode.create.pager.InputAdapter
import com.tonapps.tonkeeper.fragment.passcode.create.pager.InputHolder
import com.tonapps.tonkeeper.fragment.passcode.create.pager.InputType
import uikit.extensions.applyNavBottomPadding
import uikit.widget.NumPadView
import uikit.extensions.findViewHolderForAdapterPosition
import uikit.mvi.UiScreen

open class PasscodeCreateScreen: UiScreen<PasscodeCreateScreenState, PasscodeCreateScreenEffect, PasscodeCreateScreenFeature>(
    R.layout.fragment_passcode_create) {

    companion object {
        fun newInstance() = PasscodeCreateScreen()
    }

    override val feature: PasscodeCreateScreenFeature by viewModels()

    private lateinit var containerView: View
    private lateinit var pagerView: ViewPager2
    private lateinit var numPadView: NumPadView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        containerView = view.findViewById(R.id.passcode_container)
        containerView.applyNavBottomPadding()

        pagerView = view.findViewById(R.id.pager)
        pagerView.adapter = InputAdapter()
        pagerView.offscreenPageLimit = 2
        pagerView.isUserInputEnabled = false

        numPadView = view.findViewById(R.id.num_pad)
        numPadView.doOnBackspaceClick = {
            feature.removeNumber()
        }
        numPadView.doOnNumberClick = {
            feature.addNumber(it)
        }
    }

    open fun onPasscodeComplete(code: String) {

    }

    override fun newUiState(state: PasscodeCreateScreenState) {
        pagerView.currentItem = state.activeType.ordinal
        numPadView.backspace = state.displayBackspace
        numPadView.isEnabled = !state.isPasscodeComplete
        setCount(state)
    }

    override fun newUiEffect(effect: PasscodeCreateScreenEffect) {
        super.newUiEffect(effect)
        if (effect is PasscodeCreateScreenEffect.RepeatError) {
            findInput(InputType.REPEAT)?.setError()
        } else if (effect is PasscodeCreateScreenEffect.Valid) {
            findInput(InputType.REPEAT)?.setSuccess()
            onPasscodeComplete(effect.code)
        }
    }

    private fun setCount(state: PasscodeCreateScreenState) {
        findInput(InputType.ENTER)?.setCount(state.enteredPasscode.length)
        findInput(InputType.REPEAT)?.setCount(state.repeatedPasscode.length)
    }

    private fun findInput(type: InputType): InputHolder? {
        return pagerView.findViewHolderForAdapterPosition(type.ordinal) as? InputHolder
    }

}