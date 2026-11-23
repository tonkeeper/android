package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class MoonPayTheme(val value: String) {
    Dark("dark"),
    Light("light"),
}

sealed class MoonPayFrame {

    abstract val name: String

    abstract val presentation: MoonPayFrames.Presentation

    internal abstract val path: String?

    internal abstract fun parameters(): List<Pair<String, String?>>

    final override fun toString(): String = name

    data class CheckConnection(
        val sessionToken: String,
        val publicKeyHex: String,
        val skipKyc: Boolean? = null,
    ) : MoonPayFrame() {
        override val name: String = "checkConnection"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Headless
        override val path: String = "/platform/v1/check-connection"
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "sessionToken" to sessionToken,
            "publicKey" to publicKeyHex,
            "skipKyc" to skipKyc?.toString(),
        )
    }

    data class Connect(
        val clientToken: String,
        val publicKeyHex: String,
        val theme: MoonPayTheme?,
    ) : MoonPayFrame() {
        override val name: String = "connect"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Modal
        override val path: String = "/platform/v1/connect"
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "clientToken" to clientToken,
            "publicKey" to publicKeyHex,
            "theme" to theme?.value,
        )
    }

    data class Widget(
        val clientToken: String,
        val quoteSignature: String,
        val externalTransactionId: String? = null,
        val theme: MoonPayTheme?,
    ) : MoonPayFrame() {
        override val name: String = "widget"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Modal
        override val path: String = "/platform/v1/widget"
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "flow" to "buy",
            "clientToken" to clientToken,
            "quoteSignature" to quoteSignature,
            "externalTransactionId" to externalTransactionId,
            "theme" to theme?.value,
        )
    }

    data class Buy(
        val clientToken: String,
        val signature: String,
        val externalTransactionId: String? = null,
    ) : MoonPayFrame() {
        override val name: String = "buy"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Headless
        override val path: String = "/platform/v1/buy"
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "clientToken" to clientToken,
            "signature" to signature,
            "externalTransactionId" to externalTransactionId,
        )
    }

    // MoonPay's own button for a wallet payment (Google Pay): it shows in our screen and pays only on a tap.
    data class BuyButton(
        val clientToken: String,
        val signature: String,
        val externalTransactionId: String,
        val theme: MoonPayTheme?,
        val googlePayButtonSettings: String,
    ) : MoonPayFrame() {
        override val name: String = "buyButton"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Inline
        override val path: String = "/platform/v1/buy-button"
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "clientToken" to clientToken,
            "signature" to signature,
            "externalTransactionId" to externalTransactionId,
            "theme" to theme?.value,
            "googlePayButtonSettings" to googlePayButtonSettings,
        )
    }

    data class AddCard(
        val clientToken: String,
        val theme: MoonPayTheme?,
    ) : MoonPayFrame() {
        override val name: String = "addCard"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Modal
        override val path: String = "/platform/v1/add-card"
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "clientToken" to clientToken,
            "theme" to theme?.value,
        )
    }

    // [resume]: a wallet payment challenged before its transaction is finished on its buy button after `resolved`.
    data class Challenge(
        val url: String,
        val clientToken: String,
        val theme: MoonPayTheme?,
        val resume: Boolean,
    ) : MoonPayFrame() {
        override val name: String = "challenge"
        override val presentation: MoonPayFrames.Presentation = MoonPayFrames.Presentation.Modal
        override val path: String? = null
        override fun parameters(): List<Pair<String, String?>> = listOf(
            "clientToken" to clientToken,
            "theme" to theme?.value,
            "resume" to "true".takeIf { resume },
        )
    }

    fun url(environment: MoonPayEnvironment, channelId: String): String {
        val builder = when (this) {
            is Challenge -> url.toHttpUrlOrNull()?.takeIf { environment.isFrameOrigin(url) }?.newBuilder()
                ?: throw MoonPayFrameException.InvalidUrl(name)
            is CheckConnection,
            is Connect,
            is Widget,
            is Buy,
            is BuyButton,
            is AddCard,
            -> environment.frameOrigin.toHttpUrl().newBuilder().encodedPath(checkNotNull(path))
        }
        (parameters() + ("channelId" to channelId)).forEach { (key, value) ->
            value?.let { builder.addQueryParameter(key, it) }
        }
        return builder.build().toString()
    }
}

internal fun MoonPayEnvironment.isFrameOrigin(origin: String): Boolean {
    val url = origin.toHttpUrlOrNull() ?: return false
    val frame = frameOrigin.toHttpUrl()
    return url.scheme == frame.scheme && url.host == frame.host && url.port == frame.port
}
