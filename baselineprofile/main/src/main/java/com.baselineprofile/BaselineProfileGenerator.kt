package com.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 15,
        stableIterations = 3,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        allowNotifications()

        val hasBottomTabs = device.wait(Until.hasObject(By.res(PACKAGE_NAME, "bottom_tabs")), 1_000)
        if (!hasBottomTabs) {
            createWalletAction()
            startWalletActions()
        } else {
            startWalletActions()
        }
    }

    private fun MacrobenchmarkScope.startWalletActions() {
        sleep(2000)
        addWatchWallet("UQDYzZmfsrGzhObKJUw4gzdeIxEai3jAFbiGKGwxvxHinf4K")
        sleep(2000)
        clickTabs()
        deleteWallet()
    }

    private fun MacrobenchmarkScope.deleteWallet() {
        click("settings", 1000)
        scrollDown("list", 1000)

        val deleteWalletSelector = By.textStartsWith("Delete")
        waitVisible(deleteWalletSelector)
        device.findObject(deleteWalletSelector).click()

        val confirmDeleteWalletSelector = By.text("Delete")
        waitVisible(confirmDeleteWalletSelector)
        device.findObject(confirmDeleteWalletSelector).click()
    }

    private fun MacrobenchmarkScope.clickTabs() {
        val bottomTabsSelector = By.res(PACKAGE_NAME, "bottom_tabs")
        waitVisible(bottomTabsSelector)
        val bottomTabs = device.findObject(bottomTabsSelector)
        bottomTabs.children.drop(1).forEach {
            it.click()
            sleep(5000)
        }
        bottomTabs.children.first().click()
    }
}