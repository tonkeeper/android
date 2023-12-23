package com.tonkeeper.extensions

import com.tonkeeper.fragment.camera.CameraFragment
import com.tonkeeper.fragment.receive.ReceiveScreen
import com.tonkeeper.fragment.root.RootActivity
import com.tonkeeper.fragment.send.SendScreen
import io.tonapi.models.JettonBalance
import uikit.extensions.findFragment
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation

fun Navigation.openCamera() {
    add(CameraFragment.newInstance())
}

fun Navigation.sendCoin(
    address: String? = null,
    text: String? = null,
    amount: Float = 0f,
    jetton: JettonBalance? = null
) {
    if (this !is RootActivity) return

    val currentFragment = supportFragmentManager.findFragment<SendScreen>()
    if (currentFragment is SendScreen) {
        currentFragment.forceSetAddress(address)
        currentFragment.forceSetComment(text)
        currentFragment.forceSetAmount(amount)
        currentFragment.forceSetJetton(jetton)
    } else {
        add(SendScreen.newInstance(address, text, amount, jetton))
    }
}

fun Navigation.receive(jetton: JettonBalance? = null) {
    add(ReceiveScreen.newInstance(jetton))
}