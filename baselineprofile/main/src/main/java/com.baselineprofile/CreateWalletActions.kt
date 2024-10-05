package com.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

fun MacrobenchmarkScope.createWalletAction() {
    click("import_wallet")

    clickListItem("list", 0)

    // Enter seed phrase
    setText("word_1", "donkey caught shift recipe husband wide number enough oil number head addict color speak canal rent dry believe shine category prevent math avocado dumb")

    // Click button after entering seed phrase
    if (!click("button", 1000)) {
        setText("word_24", "")
        sleep(100)
        setText("word_24", "dumb")
        click("button", 1000)
    }

    // Click button on select wallets screen
    click("button", 2000)

    // Set pin code
    crossPinScreen()

    // Repeat pin code
    crossPinScreen()

    // Click button on push notifications screen
    click("button", 500)

    // Set wallet color
    clickListItem("label_color_picker", 2, 500)

    // Set wallet icon
    clickListItem("label_emoji_picker", 2)

    // Save wallet label
    click("label_button")
}

private fun MacrobenchmarkScope.crossPinScreen() {
    val pinButtonSelector = By.text("5")
    device.wait(Until.hasObject(pinButtonSelector), 5_000)
    val pinButton = device.findObject(pinButtonSelector)
    repeat(4) {
        pinButton.click()
        sleep(100)
    }
    sleep(300)
}