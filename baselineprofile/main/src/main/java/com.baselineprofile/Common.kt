package com.baselineprofile

import android.Manifest
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.ton_keeper"

fun sleep(ms: Long) {
    if (ms > 0) {
        Thread.sleep(ms)
    }
}

fun MacrobenchmarkScope.waitVisible(selector: BySelector): Boolean {
    return device.wait(Until.hasObject(selector), 5_000)
}

fun MacrobenchmarkScope.click(resId: String, delay: Long = 0): Boolean {
    sleep(delay)

    val selector = By.res(PACKAGE_NAME, resId)
    if (!waitVisible(selector)) {
        return false
    }

    val obj = device.findObject(selector) ?: return false
    if (!obj.isEnabled) {
        return false
    }
    obj.click()

    return true
}

fun MacrobenchmarkScope.clickListItem(resId: String, index: Int, delay: Long = 0) {
    sleep(delay)

    val selector = By.res(PACKAGE_NAME, resId)
    waitVisible(selector)

    val obj = device.findObject(selector)
    if (obj.children.size <= index) {
        obj.children.last().click()
    } else {
        obj.children[index].click()
    }
}

fun MacrobenchmarkScope.setText(resId: String, text: String, delay: Long = 0) {
    sleep(delay)

    val selector = By.res(PACKAGE_NAME, resId)
    waitVisible(selector)
    device.findObject(selector).text = text
}

fun MacrobenchmarkScope.allowNotifications() {
    if (SDK_INT >= TIRAMISU) {
        val command = "pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}"
        device.executeShellCommand(command)
    }
}

fun MacrobenchmarkScope.scrollDown(resId: String, delay: Long = 0) {
    sleep(delay)
    val selector = By.res(PACKAGE_NAME, resId)
    waitVisible(selector)
    val obj = device.findObject(selector)
    obj.swipe(Direction.UP, 0.9f)
}