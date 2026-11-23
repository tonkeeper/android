package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.bus.core.IssueHelper
import com.tonapps.log.L

// A legal text to show in full before the payment; [links] are ranges of [text] that open their url.
data class MoonPayDisclosureText(val text: String, val links: List<Link>) {

    data class Link(val start: Int, val end: Int, val url: String)
}

// The legal texts MoonPay's quote names in `paymentDisclosures`, as MoonPay's go-live guide gives them. MoonPay sends
// only their ids and versions; the copy must be shown verbatim, in English, so it is kept here rather than in the
// translated strings. The same copy as on iOS.
object MoonPayPaymentDisclosures {

    // The copy here is of these versions; a newer one means MoonPay has changed the wording.
    const val KNOWN_VERSION = "1"

    // In the quote's order, each text once. MoonPay asks for a conservative disclosure in place of one it has not told
    // us about: the transaction finality text. What the copy here may not match is [report]ed, so that release builds
    // tell of it too.
    fun texts(
        disclosures: List<MoonPayBuyQuote.PaymentDisclosure>,
        report: (String) -> Unit = ::reportMismatch,
    ): List<MoonPayDisclosureText> {
        val shown = mutableListOf<Disclosure>()
        for (disclosure in disclosures) {
            val known = Disclosure.entries.firstOrNull { it.id == disclosure.id }.takeUnless { disclosure.undecodable }
            when {
                disclosure.undecodable -> report("undecodable moonpay disclosure ${disclosure.id}")
                known == null -> report("unknown moonpay disclosure ${disclosure.id} v${disclosure.version ?: "-"}")
                disclosure.version != null && disclosure.version != KNOWN_VERSION ->
                    report("unknown moonpay disclosure ${disclosure.id} v${disclosure.version}")
            }
            val text = known ?: Disclosure.TransactionFinality
            if (text !in shown) {
                shown += text
            }
        }
        return shown.map { parse(it.markdown) }
    }

    // At error level and to Crashlytics: warnings stay on the device.
    private fun reportMismatch(message: String) {
        val error = MoonPayDisclosureMismatch(message)
        L.e(error)
        runCatching { IssueHelper.recordException(error) }
    }

    enum class Disclosure(val id: String, val markdown: String) {
        TransactionFinality(
            "us-transaction-finality",
            "I agree to MoonPay’s [Terms of Use]($TERMS) and understand that, once executed, this transaction cannot be cancelled, recalled, refunded, or otherwise undone. Fraudulent transactions may result in the loss of funds with no recourse.",
        ),
        CryptoAssetRisk(
            "eea-crypto-asset-risk",
            "By continuing, you agree to transact with MoonPay Europe, subject to its [Terms of Use]($TERMS) and [Privacy Policy]($PRIVACY_POLICY). Crypto-assets can be risky and values may decrease quickly. Transfers are irreversible once broadcast to the blockchain. The quoted exchange rate may include a spread. [Learn more]($LEGAL) and [review the whitepaper]($WHITEPAPERS) (if available).",
        ),
        UnregulatedStablecoinRisk(
            "eea-unregulated-stablecoin-risk",
            "Important: You are about to transact in a stablecoin that is not MiCA-compliant, carries fewer safeguards, and may be difficult to sell. By continuing, you agree to transact with MoonPay Europe subject to its [Terms of Use]($TERMS) and [Privacy Policy]($PRIVACY_POLICY). Transfers are irreversible once broadcast to the blockchain. The quoted exchange rate may include a spread. [Learn more]($LEGAL) and [review the whitepaper]($WHITEPAPERS) (if available).",
        ),
        GatewayToken(
            "gateway-token",
            "By proceeding, you agree to two steps under [these terms]($TERMS): (1) Buying stablecoin from MoonPay and (2) Swapping stablecoin for your chosen destination asset via a decentralised exchange.",
        ),
    }

    private fun parse(markdown: String): MoonPayDisclosureText {
        val text = StringBuilder()
        val links = mutableListOf<MoonPayDisclosureText.Link>()
        var last = 0
        for (match in LINK.findAll(markdown)) {
            text.append(markdown, last, match.range.first)
            val (title, url) = match.destructured
            val start = text.length
            text.append(title)
            links += MoonPayDisclosureText.Link(start, text.length, url)
            last = match.range.last + 1
        }
        text.append(markdown, last, markdown.length)
        return MoonPayDisclosureText(text.toString(), links)
    }
}

// The copy's only markup: `[title](url)`.
private val LINK = Regex("""\[([^\]]+)]\(([^)]+)\)""")

private class MoonPayDisclosureMismatch(message: String) : IllegalStateException(message)

private const val TERMS = "https://www.moonpay.com/legal/terms"
private const val PRIVACY_POLICY = "https://www.moonpay.com/legal/privacy_policy"
private const val LEGAL = "https://www.moonpay.com/legal"
private const val WHITEPAPERS = "https://dev.moonpay.com/docs/list-of-supported-cryptocurrencies"
