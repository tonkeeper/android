package com.tonapps.tonkeeper.extensions

import com.tonapps.tonkeeper.fragment.camera.CameraFragment
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.tonkeeper.fragment.send.SendScreen
import io.tonapi.models.JettonBalance
import uikit.extensions.findFragment
import uikit.navigation.Navigation

fun Navigation.openCamera() {
    add(CameraFragment.newInstance())
}

fun Navigation.sendCoin(
    address: String? = null,
    text: String? = null,
    amount: Float = 0f,
    jettonAddress: String? = null
) {
    if (this !is RootActivity) return

    val currentFragment = supportFragmentManager.findFragment<SendScreen>()
    if (currentFragment is SendScreen) {
        currentFragment.forceSetAddress(address)
        currentFragment.forceSetComment(text)
        currentFragment.forceSetAmount(amount)
        currentFragment.forceSetJetton(jettonAddress)
    } else {
        add(SendScreen.newInstance(address, text, amount, jettonAddress))
    }
}
