package com.tonapps.deposit.multicoin.screens.buy

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowProviderMode
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.core.components.sanitizeAmountInput
import com.tonapps.core.components.withLocaleDecimalSeparator
import com.tonapps.deposit.multicoin.analytics.BuyCryptoAnalytics
import com.tonapps.deposit.multicoin.analytics.BuyCryptoReport
import com.tonapps.deposit.multicoin.analytics.BuyCryptoTrack
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.domain.MoonPayAccess
import com.tonapps.deposit.multicoin.domain.MoonPaySignIn
import com.tonapps.deposit.multicoin.domain.RampAmountRange
import com.tonapps.deposit.multicoin.domain.RampCheckout
import com.tonapps.deposit.multicoin.domain.RampOffer
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampPayments
import com.tonapps.deposit.multicoin.domain.RampProviderOption
import com.tonapps.deposit.multicoin.domain.RampTicket
import com.tonapps.deposit.multicoin.domain.moonPayMethodNames
import com.tonapps.deposit.multicoin.domain.offersMoonPay
import com.tonapps.deposit.multicoin.domain.savedCardId
import com.tonapps.deposit.multicoin.fiatScale
import com.tonapps.deposit.multicoin.moonpay.MOONPAY_GOOGLE_PAY_TYPE
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentRetry
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.exchangeapi.models.ExchangeMerchantSlug
import java.math.BigDecimal
import java.math.RoundingMode

// The whole state of the buy screen and the one place it changes: every action of the customer and every result of
// a load, a quote or a checkout is a command, taken one at a time on the main thread. A result names what it was
// asked for, so one that came too late changes nothing.
internal class BuyCryptoReducer(
    private val moonPay: RampPayments?,
    private val analyticsFrom: DepositFlowFrom,
    private val utm: UtmParameters,
    // The external transaction id of a new buy button.
    private val newButtonId: () -> String,
) {

    // MoonPay's buy button in place of Continue (Google Pay). One per setup and amount: the frame pays the amount it
    // loaded with ([loaded]); a newer quote of them goes into it ([inFrame]).
    data class ButtonState(
        val id: String,
        val setup: Setup,
        val amount: BigDecimal,
        val loaded: MoonPayBuyQuote,
        val inFrame: MoonPayBuyQuote,
        val phase: ButtonPhase,
    )

    sealed interface ButtonPhase {
        data object Loading : ButtonPhase

        data object Ready : ButtonPhase

        // Tapped: the status sheet shows its payment until it closes.
        data object Pressed : ButtonPhase

        // Ended before a tap; tried again only with a newer quote.
        data object Ended : ButtonPhase
    }

    // [picked] is the customer's pick this setup honours; [provider] is who quotes, the pick or MoonPay by default.
    // [methods] is what the method screen lists: every method of whoever sells here, [method] among them.
    class Setup(
        val asset: RampAssetDetail?,
        val fiat: String,
        val method: RampPaymentMethod?,
        val payments: RampPayments,
        val provider: ExchangeMerchantSlug? = null,
        val picked: ExchangeMerchantSlug? = null,
        val moonPaySold: Boolean? = null,
        val methods: List<RampPaymentMethod> = listOfNotNull(method),
    )

    // [merchants] holds MoonPay's widget too; the sheet drops it where MoonPay itself is offered.
    class ProviderOptions(val merchants: List<RampProviderOption>, val moonPayMethods: List<String>)

    // A new key loads the provider sheet again: another pair, a sign-in that changes MoonPay's methods, a retry.
    data class ProvidersKey(val asset: RampAssetDetail, val fiat: String, val signedIn: Boolean, val attempt: Int)

    data class ProvidersView(val key: ProvidersKey? = null, val options: ProviderOptions? = null) {
        val failed: Boolean get() = key != null && options == null
    }

    class ProviderSheet(val rows: List<BuyCryptoProviderRow>, val line: BuyCryptoProviderLine?)

    // [pickedAsset] and [pickedMethod] show a pick until it loads; only the ids are saved.
    data class SetupRequest(
        val fiat: String?,
        val assetId: String?,
        val method: String?,
        val fiatChanges: Int = 0,
        val pickedAsset: ShownAsset? = null,
        val pickedMethod: ShownMethod? = null,
        val provider: ExchangeMerchantSlug? = null,
    )

    data class ShownAsset(val id: String, val label: String, val iconUrl: String?)

    data class ShownMethod(
        val id: String,
        val title: String,
        val subtitle: String?,
        val iconUrl: String?,
        val card: BuyCryptoCard?,
        val moonPay: Boolean,
    )

    data class ShownSetup(val fiat: String, val asset: ShownAsset?, val method: ShownMethod?, val provider: ExchangeMerchantSlug?)

    enum class LoadStatus { Loading, Ready, Failed }

    // A new key starts a new load; [recheck] checks MoonPay's connection silently before it. [load] makes every key new,
    // so an effect that sees only the latest one never mistakes a key that came back for the one it already ran.
    data class SetupKey(val request: SetupRequest, val reload: Int, val recheck: Boolean, val load: Int)

    data class QuoteKey(
        val setup: Setup,
        val amount: BigDecimal?,
        val attempt: Int,
        val refresh: Boolean,
        val request: Int,
    ) {
        val input: Pair<Setup, BigDecimal?> get() = setup to amount

        val quotable: Boolean
            get() = setup.asset != null && setup.method?.isP2P == false && (amount?.signum() ?: 0) > 0
    }

    // [input] is what the shown offer was quoted for: a refresh keeps the offer only for that input.
    data class QuoteView(
        val offer: RampOffer? = null,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val failed: Boolean = false,
        // The last quote failed for the lack of network: the rate shown is [rated]'s, for display only.
        val offline: Boolean = false,
        val input: Pair<Setup, BigDecimal?>? = null,
        val rated: Pair<Setup, RampOffer.Available>? = null,
    ) {
        val ready: RampOffer? get() = offer.takeIf { !loading }
    }

    // [suggested] is the last amount put in for the user: a new suggestion replaces only it. A field never
    // touched counts as an empty suggestion; one the user emptied, here or before a recreation, does not.
    data class AmountState(val text: String, val suggested: String?, val revision: Int)

    sealed interface Phase {
        data object Idle : Phase

        // [offer] and [setup] are what the customer continued with; [onMoonPay] signs in before the checkout, [signIn] instead of it.
        data class Continuing(
            val id: Int,
            val setup: Setup,
            val offer: RampOffer.Available?,
            val onMoonPay: Boolean,
            val signIn: Boolean,
        ) : Phase

        // MoonPay's status sheet is opening or open: its result brings the screen back.
        data object Paying : Phase

        // A merchant's widget or the P2P market is open and the screen is closing: another tap would order again.
        data object Ordered : Phase
    }

    data class Session(
        val request: SetupRequest,
        val amount: AmountState,
        // A pick made before the first load ended: only the last one counts, applied once it ends.
        val waitingPick: BuyCryptoAction? = null,
        val firstLoadEnded: Boolean = false,
        val reload: Int = 0,
        val recheck: Boolean = false,
        val loads: Int = 0,
        val loading: Boolean = true,
        val setup: Setup? = null,
        val lastLoaded: Setup? = null,
        val moonPay: MoonPayAccess? = null,
        // Pairs signed-in MoonPay did not sell on this screen, as iOS keeps them: its row leaves the provider sheet.
        val moonPayNotSold: Set<String> = emptySet(),
        val providers: ProvidersView = ProvidersView(),
        val providerAttempt: Int = 0,
        // The load a customer's Retry started: only its failure is told, once, by [providerFailures].
        val providerRetry: ProvidersKey? = null,
        val providerFailures: Int = 0,
        // Saved cards removed on the method screen: the methods loaded before the removal still list them.
        val removedCards: Set<String> = emptySet(),
        val range: RampAmountRange? = null,
        val rangeLoading: Boolean = false,
        val quoteAttempt: Int = 0,
        val quoteKey: QuoteKey? = null,
        val quoteRequests: Int = 0,
        val quote: QuoteView = QuoteView(),
        val quotesLanded: Int = 0,
        val offlineToastAtMs: Long? = null,
        val phase: Phase = Phase.Idle,
        val continues: Int = 0,
        val inverted: Boolean = false,
        val visible: Boolean = true,
        val button: ButtonState? = null,
        // Signatures of the quotes a buy button was tapped with: MoonPay is not handed one again.
        val spent: Set<String> = emptySet(),
        // The setup load and amount MoonPay's buy button last failed for, told once.
        val buttonFailed: Pair<Int, String>? = null,
        // The load a buy button's lost sign-in started: its own button failing the same way is told, not reloaded again.
        val buttonSignIn: Int? = null,
    ) {
        val status: LoadStatus
            get() = when {
                loading -> LoadStatus.Loading
                setup == null -> LoadStatus.Failed
                else -> LoadStatus.Ready
            }

        val setupKey: SetupKey get() = SetupKey(request, reload, recheck, loads)

        val providersKey: ProvidersKey?
            get() {
                val loaded = setup ?: lastLoaded ?: return null
                val asset = loaded.asset ?: return null
                return ProvidersKey(asset, loaded.fiat, moonPay == MoonPayAccess.Connected, providerAttempt)
            }

        // A load after a pick shows the pick over what was loaded before; a loaded one shows what was found.
        val shown: ShownSetup?
            get() {
                val last = lastLoaded
                return when {
                    setup != null -> ShownSetup(setup.fiat, setup.asset?.let(::shownAsset), setup.method?.let(::shownMethod), setup.picked)
                    last == null -> null
                    else -> ShownSetup(
                        fiat = request.fiat ?: last.fiat,
                        asset = request.pickedAsset?.takeIf { it.id == request.assetId } ?: last.asset?.let(::shownAsset),
                        method = request.pickedMethod?.takeIf { it.id == request.method } ?: last.method?.let(::shownMethod),
                        provider = request.provider,
                    )
                }
            }

        val canContinue: Boolean
            get() {
                val method = setup?.method ?: return false
                // A new card is added first and needs no quote, as on iOS; only an amount out of limits stops it.
                val newCard = method.isNewCard && quote.offer !is RampOffer.OutOfLimits
                return phase is Phase.Idle && !paysWithButton && (method.isP2P || newCard || signsIn || quote.ready is RampOffer.Available)
            }

        // MoonPay's Google Pay is paid from MoonPay's own button in place of Continue; a quote that needs a challenge
        // first goes through Continue, which opens it, as on iOS.
        val paysWithButton: Boolean
            get() {
                val googlePay = (setup?.method?.details as? RampPaymentMethod.Details.MoonPay)?.type == MOONPAY_GOOGLE_PAY_TYPE
                val quoted = ((quote.offer as? RampOffer.Available)?.ticket as? RampTicket.MoonPay)?.quote
                return googlePay && quoted?.executable != false
            }

        // As on iOS, the merchants' limits do not hold back a Continue that signs in to MoonPay.
        val signsIn: Boolean
            get() {
                val setup = setup ?: return false
                val method = setup.method ?: return false
                val amount = parseAmount(this.amount.text, fiatScale(setup.fiat))
                return moonPay == MoonPayAccess.SignInRequired && method.offersMoonPay(setup.fiat) && (amount?.signum() ?: 0) > 0
            }

        // The legal texts of the last MoonPay quote for the setup on screen, as on iOS: they follow the region, asset
        // and method, not the amount, so they stay while the next amount is quoted. None while a setup loads, for P2P
        // (it opens our market) or for a merchant's quote.
        val paymentDisclosures: List<MoonPayBuyQuote.PaymentDisclosure>
            get() {
                val setup = setup?.takeIf { status == LoadStatus.Ready && it.method?.isP2P == false } ?: return emptyList()
                val last = quote.rated?.takeIf { it.first === setup }?.second ?: return emptyList()
                return (last.ticket as? RampTicket.MoonPay)?.quote?.paymentDisclosures.orEmpty()
            }

        fun paysWithAnyOf(cardIds: Set<String>): Boolean {
            return setup?.method?.card?.id in cardIds || cardIds.any { savedCardId(it) == request.method }
        }
    }

    sealed interface Command {
        data class Act(val action: BuyCryptoAction) : Command
        data class Connected(val access: MoonPayAccess) : Command
        class SetupLoaded(val key: SetupKey, val setup: Setup?) : Command
        class RangeLoaded(val setup: Setup, val range: RampAmountRange?) : Command

        // [offline]: failed with no network; [atMs] when it landed, to space the toasts about it.
        class Quoted(val key: QuoteKey, val offer: RampOffer?, val failed: Boolean, val offline: Boolean, val atMs: Long) : Command
        class ProvidersLoaded(val key: ProvidersKey, val options: ProviderOptions?) : Command

        // [checkout] is null when it failed.
        class ContinueEnded(val id: Int, val checkout: RampCheckout?) : Command
        class SignInEnded(val id: Int, val signIn: MoonPaySignIn) : Command

        // MoonPay's buy button [id] is drawn.
        class ButtonReady(val id: String) : Command

        // The customer tapped MoonPay's buy button [id]: its payment already runs, the status sheet follows it, with
        // the quote the frame held then.
        class ButtonPressed(val id: String, val data: MoonPayPaymentData) : Command

        // MoonPay's buy button [id] ended before a tap; [quiet]: nothing to tell (an expired quote, a page that could not
        // draw in the background), a newer quote brings it back. [signInRequired]: MoonPay's sign-in ran out, which the
        // button cannot open on its own.
        class ButtonEnded(val id: String, val quiet: Boolean, val signInRequired: Boolean) : Command
    }

    // [tracks] are what the step means for analytics; the feature sends them.
    class Step(val session: Session, val events: List<BuyCryptoEvent> = emptyList(), val tracks: List<BuyCryptoTrack> = emptyList())

    private sealed interface AmountInput {
        data class Typed(val text: String, val fiat: String?) : AmountInput
        data class Suggested(val text: String) : AmountInput
    }

    fun reduce(session: Session, command: Command): Step {
        val events = mutableListOf<BuyCryptoEvent>()
        val next = when (command) {
            is Command.Act -> act(session, command.action, events)
            is Command.Connected -> session.copy(moonPay = command.access)
            is Command.SetupLoaded -> setupLoaded(session, command, events)
            is Command.RangeLoaded -> rangeLoaded(session, command)
            is Command.Quoted -> quoted(session, command, events)
            is Command.ProvidersLoaded -> providersLoaded(session, command)
            is Command.ContinueEnded -> continueEnded(session, command, events)
            is Command.ButtonReady -> buttonReady(session, command.id)
            is Command.ButtonPressed -> buttonPressed(session, command, events)
            is Command.ButtonEnded -> buttonEnded(session, command, events)
            is Command.SignInEnded -> signInEnded(session, command, events)
        }
        val shown = withButton(dropStaleRetry(firstLoad(next)))
        return Step(shown, events, tracks(session, next, shown, command))
    }

    // A retry of the providers is the customer's only while its load is the one to come: another pair, a sign-in or
    // a reload of «Buy crypto» supersedes it, and its failure is not told any longer.
    private fun dropStaleRetry(session: Session): Session {
        val retry = session.providerRetry ?: return session
        return if (session.loading || retry != session.providersKey) {
            session.copy(providerRetry = null)
        } else {
            session
        }
    }

    // The amount screen is seen once the provider is known: MoonPay's with its methods, a merchant with its first offer.
    // [shown] is [next] after a pick that waited for the first load: a setup it replaces at once was never on screen.
    private fun tracks(previous: Session, next: Session, shown: Session, command: Command): List<BuyCryptoTrack> {
        return when (command) {
            is Command.SetupLoaded -> {
                val setup = next.setup?.takeIf { it !== previous.setup && it === shown.setup && it.method?.isMoonPay == true }
                listOfNotNull(
                    setup?.let { BuyCryptoAnalytics.purchase(it.asset?.assetId, it.fiat, it.method, offer = null) }
                        ?.let(BuyCryptoTrack::AmountShown),
                )
            }
            is Command.Quoted -> {
                val offer = next.quote.offer as? RampOffer.Available
                val setup = command.key.setup
                listOfNotNull(
                    offer?.takeIf { next.quotesLanded != previous.quotesLanded }
                        ?.let { BuyCryptoAnalytics.purchase(setup.asset?.assetId, setup.fiat, setup.method, it) }
                        ?.let { purchase -> purchase.takeUnless { next.signsIn } ?: purchase.signingInToMoonPay() }
                        ?.let(BuyCryptoTrack::AmountShown),
                )
            }
            is Command.Act -> {
                val continuing = next.phase as? Phase.Continuing
                listOfNotNull(
                    continuing?.takeIf { previous.phase !is Phase.Continuing && it.setup.method?.isP2P == false && !it.signIn }
                        ?.let { BuyCryptoAnalytics.purchase(it.setup.asset?.assetId, it.setup.fiat, it.setup.method, it.offer) }
                        ?.let(BuyCryptoTrack::Continued),
                )
            }
            is Command.ButtonReady,
            is Command.ButtonEnded,
            -> emptyList()
            // A tap on MoonPay's button is the Continue and the handover to MoonPay at once, as on iOS.
            is Command.ButtonPressed -> {
                val setup = previous.setup
                if (next.phase !is Phase.Paying || previous.phase is Phase.Paying || setup == null) {
                    return emptyList()
                }
                val purchase = BuyCryptoAnalytics.purchase(setup.asset?.assetId, setup.fiat, setup.method, previous.quote.offer as? RampOffer.Available)
                    ?.copy(mode = DepositFlowProviderMode.Headless)
                    ?: return emptyList()
                listOf(BuyCryptoTrack.Continued(purchase), BuyCryptoTrack.ToProvider(purchase, command.data.externalTransactionId))
            }
            is Command.ContinueEnded -> {
                val continuing = previous.phase as? Phase.Continuing ?: return emptyList()
                val setup = continuing.setup
                // The checkout is where Continue went: MoonPay's payment in the app or a page outside it.
                val mode = when (command.checkout) {
                    is RampCheckout.Pay -> DepositFlowProviderMode.Headless
                    is RampCheckout.OpenUrl -> DepositFlowProviderMode.Merchant
                    else -> null
                }
                val purchase = BuyCryptoAnalytics.purchase(setup.asset?.assetId, setup.fiat, setup.method, continuing.offer)
                    ?.let { purchase -> mode?.let(purchase::withMode) ?: purchase }
                val txId = when (val checkout = command.checkout) {
                    is RampCheckout.Pay -> checkout.data.externalTransactionId
                    is RampCheckout.OpenUrl -> (continuing.offer?.ticket as? RampTicket.Merchant)?.merchantTransactionId
                    else -> null
                }
                when {
                    next.phase !is Phase.Ordered && next.phase !is Phase.Paying -> emptyList()
                    setup.method?.isP2P == true -> listOf(BuyCryptoTrack.ToP2P)
                    purchase != null && txId != null -> listOf(BuyCryptoTrack.ToProvider(purchase, txId))
                    else -> emptyList()
                }
            }
            is Command.Connected,
            is Command.ProvidersLoaded,
            is Command.RangeLoaded,
            is Command.SignInEnded,
            -> emptyList()
        }
    }

    // Before anything has loaded, the screen is on MoonPay if MoonPay is connected.
    fun onMoonPay(session: Session): Boolean {
        val moonPay = moonPay ?: return false
        val last = session.lastLoaded ?: return session.moonPay == MoonPayAccess.Connected
        return last.payments === moonPay
    }

    private fun needsCheck(session: Session): Boolean {
        return session.moonPay == MoonPayAccess.Connected || session.moonPay == MoonPayAccess.Unreachable
    }

    fun canPickMethod(setup: Setup): Boolean {
        return setup.asset != null && setup.method != null
    }

    // What the method screen shows: the setup on this screen, nothing loaded of its own. A load that ended with
    // nothing to pick is a failure to retry, a first one too.
    fun offer(session: Session): BuyCryptoOfferState {
        val shown = session.setup ?: session.lastLoaded
        val setup = session.setup
        val methods = setup?.takeIf { it.asset != null }?.methods.orEmpty().filter { it.card?.id !in session.removedCards }
        return BuyCryptoOfferState(
            assetId = shown?.asset?.assetId,
            fiat = shown?.fiat,
            loading = session.loading,
            failed = !session.loading && methods.isEmpty(),
            methods = methods,
            selected = setup?.method?.id,
        )
    }

    // MoonPay's own row while it may sell the pair here; else its widget is one of the merchants, as on iOS.
    // The line names who sells: a pick (pending one while it loads), MoonPay by default, the merchant of the
    // last quote for this setup, else the first merchant, as on iOS; the checkmark goes only on a real one.
    fun providerSheet(session: Session): ProviderSheet {
        val shown = session.setup ?: session.lastLoaded
        val asset = shown?.asset
        val fiat = shown?.fiat
        val view = session.providers
        val loaded = view.options?.takeIf { view.key?.asset == asset && view.key?.fiat == fiat }
        val offered = moonPay != null && asset != null && fiat != null && when (session.moonPay) {
            MoonPayAccess.Connected -> pairKey(asset.assetId, fiat) !in session.moonPayNotSold
            MoonPayAccess.SignInRequired -> asset.moonPayMethodNames(fiat).isNotEmpty()
            else -> false
        }
        val setup = session.setup
        // Selling here, MoonPay names its own methods for the pair; while the next load runs, the last one tells.
        val ownMethods = (setup ?: session.lastLoaded)?.takeIf { moonPay != null && it.payments === moonPay }
            ?.methods
            ?.filter { it.card == null && !it.isP2P }
            ?.map { it.title }
            ?.distinct()
        val rows = loaded?.let { options ->
            val subtitle = (ownMethods ?: options.moonPayMethods).joinToString(", ")
            val own = BuyCryptoProviderRow(ExchangeMerchantSlug.moonpay, MOONPAY_TITLE, subtitle, null, false)
            val merchants = options.merchants
                .filter { !offered || it.slug != ExchangeMerchantSlug.moonpay }
                .map { BuyCryptoProviderRow(it.slug, it.title, it.methods.joinToString(", "), it.imageUrl, false) }
            listOfNotNull(own.takeIf { offered }) + merchants
        }.orEmpty()
        val quoted = setup?.let { quotedBy(session, it) }
        val current = sellingNow(session)
        val checked = rows.map { it.copy(selected = it.id == current) }
        val firstMerchant = checked.firstOrNull { !(offered && it.isMoonPay) } ?: checked.firstOrNull()
        val name = checked.firstOrNull { it.selected }?.title
            ?: MOONPAY_TITLE.takeIf { current == ExchangeMerchantSlug.moonpay }
            ?: quoted?.title?.takeIf { current == quoted.slug }
            ?: firstMerchant?.title
        val failed = view.failed && view.key == session.providersKey
        // While «Buy crypto» loads, who sells is being decided: the sheet waits for it, as on iOS.
        val line = BuyCryptoProviderLine(name, switchable = !session.loading && (failed || checked.size > 1), failed = failed)
        return ProviderSheet(checked, line.takeIf { name != null || failed })
    }

    // Who the checkmark goes to: who sells by choice or by default, else the merchant of the quote.
    private fun sellingNow(session: Session): ExchangeMerchantSlug? {
        return sellingByChoice(session) ?: session.setup?.let { quotedBy(session, it) }?.slug
    }

    // A tap on it changes nothing: the pick, or MoonPay by default. Until a setup lands, the pick waiting for it,
    // else the last setup of the same pair.
    private fun sellingByChoice(session: Session): ExchangeMerchantSlug? {
        val setup = session.setup
        if (setup != null) {
            return setup.provider
        }
        val request = session.request
        val last = session.lastLoaded?.takeIf { last ->
            request.assetId in setOf(null, last.asset?.assetId) && request.fiat in setOf(null, last.fiat)
        }
        return request.provider ?: last?.provider
    }

    // Only a quote for the setup on screen names its merchant; it keeps naming it while the next one is asked for.
    private fun quotedBy(session: Session, setup: Setup): RampTicket.Merchant? {
        val quote = session.quote
        if (moonPay != null && setup.payments === moonPay || quote.input?.first !== setup) {
            return null
        }
        return (quote.offer as? RampOffer.Available)?.ticket as? RampTicket.Merchant
    }

    private fun act(session: Session, action: BuyCryptoAction, events: MutableList<BuyCryptoEvent>): Session {
        return when (action) {
            is BuyCryptoAction.SetAmount -> if (session.phase is Phase.Idle) {
                amount(session, AmountInput.Typed(action.text, session.shown?.fiat))
            } else {
                session
            }
            is BuyCryptoAction.Continue -> continueWith(session)
            is BuyCryptoAction.Reload -> if (session.phase is Phase.Idle) {
                reload(session)
            } else {
                session
            }
            // Off the idle screen the shown quote is what is being paid: its timer waits, and so does a retry.
            is BuyCryptoAction.Requote -> if (session.phase is Phase.Idle) {
                requote(session.copy(quoteAttempt = session.quoteAttempt + 1))
            } else {
                session
            }
            is BuyCryptoAction.RetryRate -> session
            is BuyCryptoAction.RetryProviders -> if (session.providers.failed && session.providers.key == session.providersKey && !session.loading) {
                session.copy(providerAttempt = session.providerAttempt + 1).let { it.copy(providerRetry = it.providersKey) }
            } else {
                session
            }
            is BuyCryptoAction.ToggleRateDirection -> session.copy(inverted = !session.inverted)
            is BuyCryptoAction.SetVisible -> session.copy(visible = action.visible)
            is BuyCryptoAction.PaymentEnded -> paymentEnded(session, action, events)
            // A removed card that the screen pays with, or is loading to pay with, gives way to the default method.
            // The removed card stops being the requested method, so a later removal does not reload the screen again.
            is BuyCryptoAction.CardsRemoved -> {
                val removed = session.copy(removedCards = session.removedCards + action.ids)
                if (session.phase is Phase.Idle && session.paysWithAnyOf(action.ids)) {
                    val requested = session.request.method in action.ids.map(::savedCardId)
                    val request = if (requested) {
                        session.request.copy(method = null, pickedMethod = null)
                    } else {
                        session.request
                    }
                    reload(removed.copy(request = request))
                } else {
                    removed
                }
            }
            is BuyCryptoAction.SelectFiat,
            is BuyCryptoAction.SelectAsset,
            is BuyCryptoAction.SelectMethod,
            is BuyCryptoAction.SelectProvider,
            -> if (session.firstLoadEnded) {
                pick(session, action)
            } else {
                session.copy(waitingPick = action)
            }
        }
    }

    // A pick waits for the first load to end: a screen restored under a picker has nothing on it to pick against yet.
    private fun firstLoad(session: Session): Session {
        if (session.firstLoadEnded || (session.shown == null && session.status != LoadStatus.Failed)) {
            return session
        }
        val ended = session.copy(firstLoadEnded = true, waitingPick = null)
        return session.waitingPick?.let { pick(ended, it) } ?: ended
    }

    // A pick changes one thing on the screen as it is, fallbacks included, as on iOS.
    private fun pick(session: Session, action: BuyCryptoAction): Session {
        val request = session.request
        val shown = session.shown
        val onScreen = if (shown != null) {
            SetupRequest(
                fiat = shown.fiat,
                assetId = shown.asset?.id,
                method = shown.method?.id,
                fiatChanges = request.fiatChanges,
                pickedAsset = request.pickedAsset?.takeIf { it.id == shown.asset?.id },
                pickedMethod = request.pickedMethod?.takeIf { it.id == shown.method?.id },
                provider = shown.provider,
            )
        } else {
            request
        }
        val next = when (action) {
            is BuyCryptoAction.SelectFiat -> if (action.code == onScreen.fiat) {
                request
            } else {
                onScreen.copy(fiat = action.code, fiatChanges = request.fiatChanges + 1)
            }
            is BuyCryptoAction.SelectAsset -> if (action.assetId == onScreen.assetId) {
                request
            } else {
                onScreen.copy(
                    assetId = action.assetId,
                    fiat = onScreen.fiat ?: action.fiat,
                    pickedAsset = ShownAsset(action.assetId, assetLabel(action.symbol, action.networkName), action.iconUrl),
                )
            }
            is BuyCryptoAction.SelectMethod -> if (action.id == onScreen.method) {
                request
            } else {
                onScreen.copy(
                    method = action.id,
                    fiat = onScreen.fiat ?: action.fiat,
                    pickedMethod = ShownMethod(action.id, action.title, action.subtitle, action.iconUrl, action.card, action.moonPay),
                )
            }
            // MoonPay selling by default is who sells now too, as on iOS; the merchant of the quote is pinned with
            // the method it quotes. Another provider starts from its own default method: a method id means another
            // method at another provider (a saved card or a new one).
            is BuyCryptoAction.SelectProvider -> when {
                session.loading -> request
                action.provider == sellingByChoice(session) -> request
                action.provider == session.setup?.let { quotedBy(session, it) }?.slug -> onScreen.copy(provider = action.provider)
                else -> onScreen.copy(provider = action.provider, method = null, pickedMethod = null)
            }
            else -> request
        }
        if (next == request) {
            return session
        }
        val picked = session.copy(request = next, recheck = needsCheck(session))
        val cleared = if (next.fiatChanges != request.fiatChanges) {
            picked.copy(amount = AmountState(text = "", suggested = "", revision = session.amount.revision + 1))
        } else {
            picked
        }
        return startLoad(cleared)
    }

    private fun reload(session: Session): Session {
        return startLoad(session.copy(reload = session.reload + 1, recheck = needsCheck(session)))
    }

    private fun startLoad(session: Session): Session {
        return requote(session.copy(loading = true, setup = null, range = null, rangeLoading = false, loads = session.loads + 1))
    }

    private fun setupLoaded(session: Session, loaded: Command.SetupLoaded, events: MutableList<BuyCryptoEvent>): Session {
        if (!session.loading || loaded.key != session.setupKey) {
            return session
        }
        val setup = loaded.setup
        // Off screen, the method screen shows the failure: a toast later would land on a screen that recovered.
        if (setup == null && session.lastLoaded != null && session.visible) {
            events += BuyCryptoEvent.Failed
        }
        val pair = setup?.asset?.let { pairKey(it.assetId, setup.fiat) }
        val notSold = when {
            pair == null -> session.moonPayNotSold
            setup.moonPaySold == false -> session.moonPayNotSold + pair
            setup.moonPaySold == true -> session.moonPayNotSold - pair
            else -> session.moonPayNotSold
        }
        return requote(
            session.copy(
                loading = false,
                setup = setup,
                lastLoaded = setup ?: session.lastLoaded,
                rangeLoading = setup?.method?.isP2P == false,
                moonPayNotSold = notSold,
            ),
        )
    }

    private fun providersLoaded(session: Session, loaded: Command.ProvidersLoaded): Session {
        if (loaded.key != session.providersKey) {
            return session
        }
        val retried = loaded.key == session.providerRetry
        val failures = if (retried && loaded.options == null) {
            session.providerFailures + 1
        } else {
            session.providerFailures
        }
        return session.copy(
            providers = ProvidersView(loaded.key, loaded.options),
            providerRetry = null,
            providerFailures = failures,
        )
    }

    private fun rangeLoaded(session: Session, loaded: Command.RangeLoaded): Session {
        if (!session.rangeLoading || loaded.setup !== session.setup) {
            return session
        }
        val ranged = session.copy(range = loaded.range, rangeLoading = false)
        // Off the idle screen the amount is what is being paid.
        if (session.phase !is Phase.Idle) {
            return ranged
        }
        val suggested = loaded.range?.suggested?.stripTrailingZeros()?.toPlainString() ?: return ranged
        return amount(ranged, AmountInput.Suggested(suggested))
    }

    private fun amount(session: Session, input: AmountInput): Session {
        val previous = session.amount
        val revision = previous.revision + 1
        val amount = when (input) {
            is AmountInput.Typed -> {
                val text = acceptAmount(previous.text, input.text, input.fiat)
                previous.copy(text = text, suggested = previous.suggested.takeIf { text == previous.text }, revision = revision)
            }
            is AmountInput.Suggested -> if (previous.text == previous.suggested) {
                AmountState(text = input.text, suggested = input.text, revision = revision)
            } else {
                previous
            }
        }
        // The same amount put in again, as the field does on losing focus, asks for no new quote while one is shown or
        // on its way; after a failed one it asks again, as Retry would.
        val retry = input is AmountInput.Typed && session.quote.failed && !session.quote.loading
        val attempt = if (retry) {
            session.quoteAttempt + 1
        } else {
            session.quoteAttempt
        }
        return requote(session.copy(amount = amount, quoteAttempt = attempt))
    }

    // A new input asks for a quote; the same input asked again refreshes the one shown.
    private fun requote(session: Session): Session {
        val setup = session.setup ?: return session.copy(quoteKey = null, quote = QuoteView())
        val amount = parseAmount(session.amount.text, fiatScale(setup.fiat))
        val previous = session.quoteKey
        val sameInput = previous != null && previous.setup === setup && previous.amount == amount
        if (sameInput && previous?.attempt == session.quoteAttempt) {
            return session
        }
        val request = session.quoteRequests + 1
        val key = QuoteKey(setup, amount, session.quoteAttempt, refresh = sameInput, request = request)
        val kept = session.quote
        val view = when {
            !key.quotable -> QuoteView(rated = kept.rated)
            key.refresh && kept.input == key.input && kept.offer is RampOffer.Available -> kept.copy(refreshing = true)
            else -> kept.copy(loading = true, refreshing = false, failed = false)
        }
        return session.copy(quoteKey = key, quoteRequests = request, quote = view)
    }

    private fun quoted(session: Session, quoted: Command.Quoted, events: MutableList<BuyCryptoEvent>): Session {
        if (quoted.key != session.quoteKey) {
            return session
        }
        if (quoted.offline) {
            return offline(session, quoted, events)
        }
        return session.copy(
            quote = QuoteView(
                offer = quoted.offer,
                failed = quoted.failed,
                input = quoted.key.input,
                rated = (quoted.offer as? RampOffer.Available)?.let { quoted.key.setup to it }
                    ?: session.quote.rated?.takeIf { it.first === quoted.key.setup },
            ),
            quotesLanded = session.quotesLanded + 1,
            // A quote that went through ends the offline spell: the next one tells again.
            offlineToastAtMs = session.offlineToastAtMs.takeIf { quoted.failed },
        )
    }

    // As on iOS: the last rate stays, the toast follows every input the user made and at most every 30 s a refresh.
    private fun offline(session: Session, quoted: Command.Quoted, events: MutableList<BuyCryptoEvent>): Session {
        val last = session.offlineToastAtMs
        val toast = !quoted.key.refresh || last == null || quoted.atMs - last >= OFFLINE_TOAST_INTERVAL_MS
        if (toast) {
            events += BuyCryptoEvent.Offline
        }
        return session.copy(
            quote = QuoteView(
                offline = true,
                input = quoted.key.input,
                rated = session.quote.rated?.takeIf { it.first === quoted.key.setup },
            ),
            quotesLanded = session.quotesLanded + 1,
            offlineToastAtMs = quoted.atMs.takeIf { toast } ?: last,
        )
    }

    private fun continueWith(session: Session): Session {
        val setup = session.setup
        if (!session.canContinue || setup == null) {
            return session
        }
        val id = session.continues + 1
        val offer = session.quote.ready as? RampOffer.Available
        return session.copy(continues = id, phase = Phase.Continuing(id, setup, offer, onMoonPay(session), signIn = session.signsIn))
    }

    private fun signInEnded(session: Session, ended: Command.SignInEnded, events: MutableList<BuyCryptoEvent>): Session {
        val phase = session.phase
        if (phase !is Phase.Continuing || phase.id != ended.id) {
            return session
        }
        val idle = session.copy(phase = Phase.Idle)
        return when (ended.signIn) {
            is MoonPaySignIn.SignedIn -> startLoad(
                idle.copy(
                    moonPay = MoonPayAccess.Connected,
                    moonPayNotSold = emptySet(),
                    amount = idle.amount.copy(suggested = null),
                    reload = session.reload + 1,
                    recheck = false,
                ),
            )
            is MoonPaySignIn.Closed -> idle
            is MoonPaySignIn.Refused -> {
                events += BuyCryptoEvent.Failed
                val refused = idle.copy(moonPay = MoonPayAccess.Unavailable)
                val widgetByDefault = phase.setup.provider == ExchangeMerchantSlug.moonpay && phase.setup.picked == null
                if (phase.onMoonPay || widgetByDefault) {
                    startLoad(refused.copy(reload = session.reload + 1, recheck = false))
                } else {
                    refused
                }
            }
            is MoonPaySignIn.Failed -> {
                events += BuyCryptoEvent.Failed
                idle
            }
        }
    }

    // What the screen does with MoonPay's buy button after every step: start one for a quote MoonPay may be handed,
    // hand a newer quote of the same setup and amount to it, drop it for another one or none. A tapped one stays
    // until the status sheet closes.
    private fun withButton(session: Session): Session {
        val current = session.button
        if (current?.phase == ButtonPhase.Pressed) {
            return session
        }
        val setup = session.setup?.takeIf {
            session.paysWithButton && session.visible && session.phase is Phase.Idle && onMoonPay(session)
        }
        val amount = session.quoteKey?.amount?.takeIf { session.quoteKey?.setup === setup }
        if (setup == null || amount == null) {
            return session.copy(button = null)
        }
        val offer = session.quote.ready as? RampOffer.Available
        val quote = (offer?.ticket as? RampTicket.MoonPay)?.quote?.takeIf { it.executable && it.signature !in session.spent }
        val same = current != null && current.setup === setup && current.amount.compareTo(amount) == 0
        return when {
            // A quote of the setup reloads, for this amount or another: the button stays, dimmed, until it lands.
            current != null && current.setup === setup && quote == null -> session
            same && current?.phase != ButtonPhase.Ended -> if (current?.inFrame == quote) {
                session
            } else {
                session.copy(button = current?.copy(inFrame = checkNotNull(quote)))
            }
            // An ended one comes back only with a newer quote: the same one would end again.
            same && (quote?.signature == current?.loaded?.signature || quote?.signature == current?.inFrame?.signature) -> session
            quote == null -> session.copy(button = null)
            else -> session.copy(
                button = ButtonState(newButtonId(), setup, amount, loaded = quote, inFrame = quote, ButtonPhase.Loading),
            )
        }
    }

    private fun buttonReady(session: Session, id: String): Session {
        val button = session.button?.takeIf { it.id == id && it.phase == ButtonPhase.Loading } ?: return session
        return session.copy(button = button.copy(phase = ButtonPhase.Ready))
    }

    // Told once per setup load and amount: the button tries again on every newer quote. A frame lost with a screen
    // going under another is not a failure to tell, nor an expired quote the next one replaces.
    private fun buttonEnded(session: Session, ended: Command.ButtonEnded, events: MutableList<BuyCryptoEvent>): Session {
        val button = session.button?.takeIf { it.id == ended.id && it.phase != ButtonPhase.Pressed } ?: return session
        val next = session.copy(button = button.copy(phase = ButtonPhase.Ended))
        // MoonPay is checked again, as Reload does: a sign-in it asks for moves the screen to the merchants, and
        // Continue opens it.
        if (ended.signInRequired && session.buttonSignIn != session.loads) {
            return reload(next).let { it.copy(buttonSignIn = it.loads) }
        }
        val failed = session.loads to session.amount.text
        if (ended.quiet || !session.visible || failed == session.buttonFailed) {
            return next
        }
        events += BuyCryptoEvent.Failed
        return next.copy(buttonFailed = failed)
    }

    // The payment already runs in MoonPay's button, so the sheet opens whatever the screen does, unless it is open.
    private fun buttonPressed(session: Session, pressed: Command.ButtonPressed, events: MutableList<BuyCryptoEvent>): Session {
        val spent = session.spent + pressed.data.quote.signature
        if (session.phase is Phase.Paying) {
            return session.copy(spent = spent)
        }
        val setup = session.setup
        val asset = setup?.asset
        val offer = session.quote.offer as? RampOffer.Available
        val purchase = setup?.let { BuyCryptoAnalytics.purchase(asset?.assetId, it.fiat, it.method, offer) }
            ?.copy(mode = DepositFlowProviderMode.Headless)
        events += BuyCryptoEvent.OpenMoonPay(
            pressed.data.copy(
                assetLabel = asset?.let { assetLabel(it.symbol, it.networkName) },
                report = purchase?.let { BuyCryptoReport(analyticsFrom, utm, it) },
            ),
        )
        val button = session.button?.takeIf { it.id == pressed.id }?.copy(phase = ButtonPhase.Pressed)
        return session.copy(phase = Phase.Paying, spent = spent, button = button)
    }

    private fun continueEnded(session: Session, ended: Command.ContinueEnded, events: MutableList<BuyCryptoEvent>): Session {
        val phase = session.phase
        if (phase !is Phase.Continuing || phase.id != ended.id) {
            return session
        }
        val idle = session.copy(phase = Phase.Idle)
        return when (val checkout = ended.checkout) {
            is RampCheckout.OpenUrl -> {
                events += BuyCryptoEvent.OpenUrl(checkout.url)
                session.copy(phase = Phase.Ordered)
            }
            is RampCheckout.Pay -> {
                // The asset Continue was tapped for: a pick made while the checkout ran must not rename it.
                val asset = phase.setup.asset
                val purchase = BuyCryptoAnalytics.purchase(asset?.assetId, phase.setup.fiat, phase.setup.method, phase.offer)
                    ?.copy(mode = DepositFlowProviderMode.Headless)
                events += BuyCryptoEvent.OpenMoonPay(
                    checkout.data.copy(
                        assetLabel = asset?.let { assetLabel(it.symbol, it.networkName) },
                        report = purchase?.let { BuyCryptoReport(analyticsFrom, utm, it) },
                    ),
                )
                session.copy(phase = Phase.Paying)
            }
            is RampCheckout.Reload -> reload(idle)
            // The added card is what the screen pays with next, or the default method if MoonPay does not list it yet.
            is RampCheckout.CardAdded -> reload(idle.copy(request = idle.request.copy(method = savedCardId(checkout.cardId), pickedMethod = null)))
            is RampCheckout.ReloadAfterFailure -> {
                events += BuyCryptoEvent.Failed
                reload(idle)
            }
            is RampCheckout.Cancelled -> idle
            null -> {
                events += BuyCryptoEvent.Failed
                idle
            }
        }
    }

    // The sheet's result brings the screen back, once: a second tap on the closing sheet changes nothing.
    // "Another card" or "another method" opens the method picker on what is loaded.
    private fun paymentEnded(session: Session, ended: BuyCryptoAction.PaymentEnded, events: MutableList<BuyCryptoEvent>): Session {
        if (session.phase !is Phase.Paying) {
            return session
        }
        if (ended.retry == MoonPayPaymentRetry.AnotherCard || ended.retry == MoonPayPaymentRetry.AnotherMethod) {
            session.setup?.takeIf(::canPickMethod)?.let { events += BuyCryptoEvent.PickMethod }
        }
        val idle = session.copy(phase = Phase.Idle)
        // The tapped quote is spent: a new button waits for a newer one.
        if (session.button?.phase == ButtonPhase.Pressed) {
            return requote(idle.copy(button = null, quoteAttempt = idle.quoteAttempt + 1))
        }
        return idle
    }
}

internal fun shownAsset(asset: RampAssetDetail): BuyCryptoReducer.ShownAsset {
    return BuyCryptoReducer.ShownAsset(asset.assetId, assetLabel(asset.symbol, asset.networkName), asset.image)
}

internal fun shownMethod(method: RampPaymentMethod): BuyCryptoReducer.ShownMethod {
    return BuyCryptoReducer.ShownMethod(method.id, method.title, method.subtitle, method.iconUrl, method.card?.icon(), method.isMoonPay)
}

// The app's reading of an asset id, its name and decimals aside.
internal fun parseAsset(assetId: String): Asset? =
    AssetEntity(id = assetId, name = "", symbol = "", decimals = 0, imageUrl = "").valueOrNull

internal fun assetLabel(symbol: String, networkName: String?): String {
    val network = networkName?.takeIf { !it.equals(symbol, ignoreCase = true) }
    return network?.let { "$symbol ($it)" } ?: symbol
}

internal fun parseAmount(text: String, scale: Int): BigDecimal? {
    return text.replace(',', '.').toBigDecimalOrNull()?.setScale(scale, RoundingMode.DOWN)
}

// Up to a billion. A zero typed after a lone zero starts the decimals ("0" then "0" is "0.0", the next zeros fill them up
// to the fiat's precision); other leading zeros go ("05" is "5").
private fun acceptAmount(previous: String, text: String, fiat: String?): String {
    val scale = fiatScale(fiat ?: WalletCurrency.USD.code)
    val sanitized = text.sanitizeAmountInput(scale)
    val separator = sanitized.indexOfFirst { it == '.' || it == ',' }.takeIf { it >= 0 } ?: sanitized.length
    val integer = sanitized.substring(0, separator)
    val fraction = sanitized.substring(separator)
    val normalized = when {
        previous == "0" && sanitized == "00" && scale > 0 -> "0" + decimalSeparator() + "0"
        integer.length > 1 && integer.startsWith('0') -> integer.trimStart('0').ifEmpty { "0" } + fraction
        else -> sanitized
    }
    val value = parseAmount(normalized, scale)
    return normalized.takeUnless { value != null && value > MAX_AMOUNT } ?: previous
}

private fun decimalSeparator(): String = "0.".withLocaleDecimalSeparator().drop(1)

internal val MAX_AMOUNT = BigDecimal(1_000_000_000)

internal fun pairKey(assetId: String, fiat: String): String = "$assetId|$fiat"

private const val MOONPAY_TITLE = "MoonPay"

private const val OFFLINE_TOAST_INTERVAL_MS = 30_000L
