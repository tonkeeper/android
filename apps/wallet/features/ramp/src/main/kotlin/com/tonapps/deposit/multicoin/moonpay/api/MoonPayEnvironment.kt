package com.tonapps.deposit.multicoin.moonpay.api

enum class MoonPayEnvironment(val apiBaseUrl: String, val frameOrigin: String) {
    Production("https://api.moonpay.com", FRAME_ORIGIN),
    Sandbox("https://api.moonpay.dev", FRAME_ORIGIN),
}

private const val FRAME_ORIGIN = "https://platform.moonpay.com"
