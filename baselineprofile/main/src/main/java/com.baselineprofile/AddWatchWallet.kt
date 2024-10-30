package com.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

fun MacrobenchmarkScope.addWatchWallet(walletAddress: String) {
    // Open wallet picker
    click("wallet")
    sleep(2000)

    // Click on add wallet
    click("add")

    // Click on add watch wallet
    clickByText("Watch Account", 5000)

    // Enter wallet address
    setText("input_field", walletAddress, 3000)

    // Click on continue on add watch wallet screen
    click("button", 5000)

    // Click on continue on push notification screen
    clickByText("Enable notification", 5000)

    // Set wallet color
    clickListItem("label_color_picker", 4, 5000)

    // Set wallet icon
    clickListItem("label_emoji_picker", 25, 500)

    // Save wallet label
    click("label_button")
}