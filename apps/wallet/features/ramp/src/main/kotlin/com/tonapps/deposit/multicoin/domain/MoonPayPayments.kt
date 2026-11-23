package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.catchingResult
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.multicoin.fiatScale
import com.tonapps.deposit.multicoin.moonpay.MoonPayAssetRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionRepository
import com.tonapps.deposit.multicoin.moonpay.MOONPAY_GOOGLE_PAY_TYPE
import com.tonapps.deposit.multicoin.moonpay.MoonPayMethodSupport
import com.tonapps.deposit.multicoin.moonpay.api.MOONPAY_CARD_TYPE
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPaySavedCard
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyAmountLimit
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuoteRequest
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodReference
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPlatformApi
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayWalletReference
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrame
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameEvent
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.deposit.multicoin.moonpay.withSilentConnection
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.log.L
import com.tonapps.wallet.localization.Localization
import io.exchangeapi.models.MoonPayPaymentMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Collections
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException

class MoonPayPayments(
    private val walletId: String,
    private val connections: MoonPayConnectionRepository,
    private val assets: MoonPayAssetRepository,
    private val frames: MoonPayFrames,
    private val rampRepository: RampRepository,
    private val exchangeRepository: ExchangeRepository,
    private val walletAddress: RampWalletAddress,
    private val platformApi: (MoonPayEnvironment) -> MoonPayPlatformApi,
    private val theme: MoonPayTheme?,
    private val string: (Int) -> String,
    private val removedCards: MoonPayRemovedCards,
    private val methodSupport: MoonPayMethodSupport,
) : RampPayments {

    private val paid: MutableSet<MoonPayBuyQuote> = Collections.synchronizedSet(mutableSetOf())

    override suspend fun loadMethods(assetId: String, symbol: String, fiat: String): Result<List<RampPaymentMethod>?> {
        return catchingResult { methods(assetId, symbol, fiat) }
            .onFailure { L.w(it, "MoonPay methods failed") }
    }

    private suspend fun methods(assetId: String, symbol: String, fiat: String): List<RampPaymentMethod>? = coroutineScope {
        val info = async {
            catchingResult { exchangeRepository.getMoonPayPaymentMethods() }
                .onFailure { L.w(it, "MoonPay method names failed") }
                .getOrDefault(emptyList())
        }
        val p2p = async { p2pMethod(assetId, fiat) }
        val found = async { connections.withSilentConnection(theme) { assets.find(it, assetId, symbol) }.getOrThrow() }
        val loaded = connections.withSilentConnection(theme) { connection ->
            platformApi(connection.environment).paymentMethods(connection.credentials.accessToken, fiat.lowercase())
        }.getOrThrow()
        val destination = found.await() ?: return@coroutineScope null
        val byType = info.await().associateBy { it.type }
        val configs = loaded.activeConfigs.filter { it.supportsBuy && methodSupport.supports(it.type) }
        val own = configs.map { config ->
            val details = RampPaymentMethod.Details.MoonPay(type = config.type, assetId = assetId, destination = destination.quoteReference)
            paymentMethod(details, byType[config.type])
        }
        if (own.isEmpty()) {
            return@coroutineScope null
        }
        // Saved cards come first and only while MoonPay takes cards; the card method itself is a new card.
        val cardConfig = configs.firstOrNull { it.type == MOONPAY_CARD_TYPE }
        val saved = cardConfig?.let { config ->
            val removable = config.capabilities?.allowsDeletion ?: true
            loaded.stored
                .filter { it.id !in removedCards }
                .map { stored -> savedCardMethod(stored, assetId, destination.quoteReference, removable) }
        }.orEmpty()
        saved + own + listOfNotNull(p2p.await())
    }

    // Out of every load from the start, back only if the removal fails. A 404 is a card already gone, or one MoonPay
    // does not let this account remove: either way the screen does not offer it again in this run, as on iOS.
    override suspend fun removeCard(id: String): Result<Unit> {
        removedCards.add(id)
        val result = try {
            catchingResult {
                connections.withSilentConnection(theme) { connection ->
                    platformApi(connection.environment).deletePaymentMethod(connection.credentials.accessToken, id)
                }.recover { error ->
                    if ((error as? MoonPayApiException.Http)?.status != HTTP_NOT_FOUND) {
                        throw error
                    }
                }.getOrThrow()
            }
        } catch (e: CancellationException) {
            removedCards.remove(id)
            throw e
        }
        return result.onFailure { removedCards.remove(id) }
    }

    // P2P comes from our backend, not MoonPay: failing to load it only hides it.
    private suspend fun p2pMethod(assetId: String, fiat: String): RampPaymentMethod? {
        return catchingResult { nullIfNotSold { rampRepository.getAsset(walletId, RampType.RampOn, assetId, fiat) } }
            .onFailure { L.w(it, "P2P for MoonPay failed") }
            .getOrNull()
            ?.methods
            ?.firstOrNull { method -> method.isP2P && method.providers.any { it.fiat == fiat } }
            ?.toPaymentMethod()
    }

    // MoonPay has no limits endpoint, but rejects a quote under the minimum with it. The maximum is only
    // known once an amount goes over it.
    override suspend fun loadAmountRange(method: RampPaymentMethod, fiat: String): Result<RampAmountRange> = catchingResult {
        val quote = quote(method.moonPay, fiat, BigDecimal.ZERO.toPlainString())
        val limit = (quote.exceptionOrNull() as? MoonPayApiException.Http)?.buyAmountLimit
        val minimum = (limit as? MoonPayBuyAmountLimit.BelowMinimum)?.amount
        if (minimum == null && quote.isFailure) {
            quote.getOrThrow()
        }
        RampAmountRange(suggested = minimum?.setScale(fiatScale(fiat), RoundingMode.UP), max = null)
    }

    override suspend fun requestOffer(request: RampQuoteRequest): Result<RampOffer> = catchingResult {
        val amount = request.amount.setScale(fiatScale(request.fiat), RoundingMode.UP)
        require(amount.signum() > 0) { "No amount to quote" }
        val quote = quote(request.method.moonPay, request.fiat, amount.toPlainString())
        val violation = when (val limit = (quote.exceptionOrNull() as? MoonPayApiException.Http)?.buyAmountLimit) {
            // MoonPay checks the exact limit, so it is shown at the fiat's precision on the side that passes.
            is MoonPayBuyAmountLimit.BelowMinimum -> RampLimitViolation.BelowMin(limit.amount.setScale(amount.scale(), RoundingMode.UP))
            is MoonPayBuyAmountLimit.AboveMaximum -> RampLimitViolation.AboveMax(limit.amount.setScale(amount.scale(), RoundingMode.DOWN))
            null -> null
        }
        // MoonPay's methods show its bundled badge, so its offers carry no merchant image.
        if (violation != null) {
            return@catchingResult RampOffer.OutOfLimits(badgeUrl = null, violation)
        }
        offer(request.copy(amount = amount), quote.getOrThrow())
    }

    override suspend fun checkout(method: RampPaymentMethod, offer: RampOffer.Available?): Result<RampCheckout> = catchingResult {
        if (method.isNewCard) {
            return@catchingResult addCard()
        }
        requireNotNull(offer) { "No offer to pay" }
        val ticket = requireNotNull(offer.ticket as? RampTicket.MoonPay) { "Not a MoonPay offer" }
        // The payment frame needs the quote for a few seconds after it opens, and one handed to MoonPay is not paid
        // again: a new one is paid at once, as on iOS.
        val quote = ticket.quote.takeIf { it !in paid && it.isFresh(MIN_QUOTE_LIFETIME) }
            ?: quote(offer.request.method.moonPay, offer.request.fiat, offer.request.amount.toPlainString()).getOrThrow()
        if (!quote.executable) {
            val url = requireNotNull(quote.challengeUrl) { "A quote that cannot be paid without a challenge" }
            return@catchingResult challenge(url)
        }
        // A Google Pay quote is paid on MoonPay's button: the screen reloads and shows it.
        if (quote.paymentMethod?.type == MOONPAY_GOOGLE_PAY_TYPE) {
            return@catchingResult RampCheckout.Reload
        }
        paid += quote
        RampCheckout.Pay(
            MoonPayPaymentData(
                quote = quote,
                paymentTitle = offer.request.method.title,
                externalTransactionId = UUID.randomUUID().toString(),
                theme = theme,
            ),
        )
    }

    override fun buttonPayment(quote: MoonPayBuyQuote, title: String, externalTransactionId: String): MoonPayPaymentData? {
        if (quote.paymentMethod?.type != MOONPAY_GOOGLE_PAY_TYPE || !quote.executable) {
            return null
        }
        return MoonPayPaymentData(quote = quote, paymentTitle = title, externalTransactionId = externalTransactionId, theme = theme)
    }

    private suspend fun quote(method: RampPaymentMethod.Details.MoonPay, fiat: String, amount: String): Result<MoonPayBuyQuote> {
        val request = MoonPayBuyQuoteRequest(
            source = MoonPayMoney(amount = amount, asset = MoonPayAsset(code = fiat.lowercase())),
            destination = MoonPayMoney(asset = method.destination),
            wallet = MoonPayWalletReference(address = walletAddress.of(walletId, method.assetId)),
            paymentMethod = MoonPayPaymentMethodReference(type = method.cardType ?: method.type, id = method.cardId),
        )
        return connections.withSilentConnection(theme) { connection ->
            platformApi(connection.environment).buyQuote(connection.credentials.accessToken, request)
        }
    }

    private fun offer(request: RampQuoteRequest, quote: MoonPayBuyQuote): RampOffer.Available {
        val receive = requireNotNull(quote.destination.amount?.toBigDecimalOrNull()) { "A quote without the amount received" }
        // `exchangeRate` is the fiat price of one unit of the asset.
        val price = quote.exchangeRate?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        return RampOffer.Available(
            request = request,
            receive = receive,
            rate = price?.let { BigDecimal.ONE.divide(it, MathContext.DECIMAL64) },
            lifetime = quote.lifetime,
            badgeUrl = null,
            ticket = RampTicket.MoonPay(quote),
        )
    }

    private suspend fun addCard(): RampCheckout {
        val connection = connections.ensureConnected(theme, interactive = true).getOrThrow()
        val frame = MoonPayFrame.AddCard(connection.credentials.clientToken, theme)
        val end = frames.run(frame, connection.environment)
            .mapNotNull { event ->
                when (event) {
                    is MoonPayFrameEvent.Complete -> (event.completion as? MoonPayFrameCompletion.Card)?.let { RampCheckout.CardAdded(it.card.id) }
                    is MoonPayFrameEvent.Cancelled -> RampCheckout.Cancelled
                    is MoonPayFrameEvent.Error -> error("The card was not added: ${event.error.code}")
                    is MoonPayFrameEvent.Handshake,
                    is MoonPayFrameEvent.Ready,
                    is MoonPayFrameEvent.TransactionCreated,
                    is MoonPayFrameEvent.Challenge,
                    is MoonPayFrameEvent.ButtonPressed,
                    is MoonPayFrameEvent.Resolved,
                    is MoonPayFrameEvent.Unknown,
                    -> null
                }
            }
            .firstOrNull()
        return end ?: RampCheckout.Cancelled
    }

    // Whatever the challenge ended with, the screen loads again, as on iOS; a failure is also shown.
    private suspend fun challenge(url: String): RampCheckout {
        val end = try {
            val connection = connections.ensureConnected(theme, interactive = true).getOrThrow()
            frames.run(MoonPayFrame.Challenge(url, connection.credentials.clientToken, theme, resume = false), connection.environment)
                .firstOrNull { it is MoonPayFrameEvent.Complete || it is MoonPayFrameEvent.Cancelled || it is MoonPayFrameEvent.Error }
        } catch (e: CancellationException) {
            throw e
        } catch (_: MoonPayConnectionException.Cancelled) {
            null
        } catch (e: Exception) {
            L.w(e, "MoonPay challenge failed")
            return RampCheckout.ReloadAfterFailure
        }
        if (end is MoonPayFrameEvent.Error) {
            L.w("MoonPay challenge failed: ${end.error.code}")
            return RampCheckout.ReloadAfterFailure
        }
        return RampCheckout.Reload
    }

    private fun paymentMethod(details: RampPaymentMethod.Details.MoonPay, info: MoonPayPaymentMethod?): RampPaymentMethod {
        return RampPaymentMethod(
            id = details.type,
            title = info?.title ?: fallbackTitle(details.type),
            subtitle = info?.subtitle,
            iconUrl = info?.iconUrl?.takeIf { it.isNotBlank() },
            badge = info?.badge,
            details = details,
        )
    }

    private fun savedCardMethod(
        stored: MoonPaySavedCard,
        assetId: String,
        destination: MoonPayAsset,
        removable: Boolean,
    ): RampPaymentMethod {
        val card = RampSavedCard(
            id = stored.id,
            kind = stored.cardType,
            brand = stored.brand,
            last4 = stored.last4,
            expiryMonth = stored.expirationMonth?.toIntOrNull(),
            expiryYear = stored.expirationYear?.toIntOrNull(),
            // As on iOS: a card MoonPay sends without availability is usable.
            active = stored.availability?.active ?: true,
            removable = removable,
        )
        val details = RampPaymentMethod.Details.MoonPay(
            type = MOONPAY_CARD_TYPE,
            assetId = assetId,
            destination = destination,
            cardId = stored.id,
            cardType = stored.type,
        )
        return RampPaymentMethod(
            id = savedCardId(stored.id),
            title = cardTitle(stored.cardType),
            subtitle = cardNumber(stored.brand, stored.last4),
            iconUrl = null,
            badge = null,
            details = details,
            card = card,
        )
    }

    private fun cardTitle(kind: String?): String {
        return when (kind?.lowercase()) {
            CREDIT_KIND -> string(Localization.moonpay_card_credit)
            DEBIT_KIND -> string(Localization.moonpay_card_debit)
            else -> string(Localization.moonpay_card)
        }
    }

    private fun cardNumber(brand: String?, last4: String?): String {
        val name = brand?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.split(' ')
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
            ?: string(Localization.moonpay_card)
        return "$name •••• ${last4?.takeIf { it.isNotBlank() } ?: "••••"}"
    }

    private fun fallbackTitle(type: String): String {
        return when (type) {
            MOONPAY_CARD_TYPE -> string(Localization.moonpay_payment_method_debit_card)
            PAYPAL_TYPE -> string(Localization.moonpay_payment_method_paypal)
            else -> type.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
        }
    }

    private companion object {
        const val PAYPAL_TYPE = "paypal"
        const val CREDIT_KIND = "credit"
        const val DEBIT_KIND = "debit"
        const val HTTP_NOT_FOUND = 404

        val MIN_QUOTE_LIFETIME = 3.seconds
    }
}

private val RampPaymentMethod.moonPay: RampPaymentMethod.Details.MoonPay
    get() = requireNotNull(details as? RampPaymentMethod.Details.MoonPay) { "Not a MoonPay method: $id" }
