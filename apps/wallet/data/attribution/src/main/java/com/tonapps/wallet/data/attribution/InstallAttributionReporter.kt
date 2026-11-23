package com.tonapps.wallet.data.attribution

private const val FRESH_INSTALL_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

internal class InstallAttributionReporter(
    private val markInstallReported: () -> Boolean,
    private val firstInstallTime: () -> Long?,
    private val currentTime: () -> Long = System::currentTimeMillis,
) {

    fun reportOnce(sendEvent: () -> Unit) {
        val installedAt = firstInstallTime() ?: return
        if (!markInstallReported() || currentTime() - installedAt > FRESH_INSTALL_WINDOW_MILLIS) {
            return
        }
        sendEvent()
    }
}
