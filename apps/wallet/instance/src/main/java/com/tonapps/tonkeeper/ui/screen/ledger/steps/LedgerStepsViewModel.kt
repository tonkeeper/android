package com.tonapps.tonkeeper.ui.screen.ledger.steps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.Item
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import uikit.extensions.context

class LedgerStepsViewModel(app: Application, private val showConfirmTxStep: Boolean) :
    AndroidViewModel(app) {

    private val _currentStepFlow = MutableStateFlow<LedgerStep>(LedgerStep.CONNECT)
    val currentStepFlow = _currentStepFlow.asSharedFlow()

    val uiItemsFlow = currentStepFlow.map { currentStep ->
        createList(currentStep)
    }

    fun setCurrentStep(step: LedgerStep) {
        _currentStepFlow.value = step
    }

    private fun createList(currentStep: LedgerStep): List<Item> {
        val uiItems = mutableListOf<Item>()

        uiItems.add(
            Item.Step(
                context.getString(Localization.ledger_connect),
                currentStep !== LedgerStep.CONNECT,
                currentStep == LedgerStep.CONNECT
            )
        )

        uiItems.add(
            Item.Step(
                context.getString(Localization.ledger_open_ton_app),
                currentStep == LedgerStep.DONE || currentStep == LedgerStep.CONFIRM_TX,
                currentStep == LedgerStep.OPEN_TON_APP
            )
        )

        if (showConfirmTxStep) {
            uiItems.add(
                Item.Step(
                    context.getString(Localization.ledger_confirm_tx),
                    currentStep == LedgerStep.DONE,
                    currentStep == LedgerStep.CONFIRM_TX
                )
            )
        }

        return uiItems
    }
}