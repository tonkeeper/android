package com.tonapps.bus.core.aptabase

private const val SELF_HOSTED_REGION = "SH"
private const val APP_KEY_PARTS = 3

object AptabaseEndpoint {

    private val hosts = mapOf(
        "US" to "https://us.aptabase.com",
        "EU" to "https://eu.aptabase.com",
        "DEV" to "http://localhost:3000",
    )

    fun resolve(appKey: String, selfHostedUrl: String?): String? {
        val parts = appKey.split("-")
        if (parts.size != APP_KEY_PARTS) {
            return null
        }
        val region = parts[1]
        if (region == SELF_HOSTED_REGION) {
            return selfHostedUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        }
        return hosts[region]
    }
}
