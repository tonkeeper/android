package com.tonapps.core.deeplink

private const val REPEAT_WINDOW_MS = 2_000L

class DAppDeepLinkGate(private val repeatWindowMs: Long = REPEAT_WINDOW_MS) {

    private val openingUrls = mutableSetOf<String>()

    private var lastOpenedUrl: String? = null

    private var lastOpenedAt = 0L

    private var isLastScreenOpenOrPending: () -> Boolean = { false }

    @Synchronized
    fun tryEnter(url: String, now: Long): Boolean {
        if (url in openingUrls) {
            return false
        }
        if (url == lastOpenedUrl && now - lastOpenedAt < repeatWindowMs && isLastScreenOpenOrPending()) {
            return false
        }
        openingUrls.add(url)
        return true
    }

    @Synchronized
    fun exit(url: String, now: Long, isScreenOpenOrPending: () -> Boolean) {
        openingUrls.remove(url)
        lastOpenedUrl = url
        lastOpenedAt = now
        isLastScreenOpenOrPending = isScreenOpenOrPending
    }
}
