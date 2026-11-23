package com.tonapps.deposit.multicoin.screens.buy.method

import androidx.lifecycle.SavedStateHandle
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.BuyCryptoAnalytics
import com.tonapps.deposit.multicoin.analytics.BuyCryptoTrack
import com.tonapps.deposit.multicoin.domain.MoonPayRamp
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampSavedCard
import com.tonapps.deposit.multicoin.domain.RampSource
import com.tonapps.deposit.multicoin.domain.preferred
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoAction
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoCard
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoOffer
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoProviderLine
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoProviderRow
import com.tonapps.deposit.multicoin.screens.buy.icon
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KState
import io.exchangeapi.models.ExchangeMerchantSlug
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext

data class BuyCryptoMethodData(
    val walletId: String,
    val analyticsFrom: DepositFlowFrom,
    val utm: UtmParameters,
)

// [card] is set for a saved card; [enabled] is false for one MoonPay no longer takes.
data class BuyCryptoMethodRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val badge: String?,
    val iconUrl: String?,
    val selected: Boolean,
    val enabled: Boolean = true,
    val card: BuyCryptoSavedCard? = null,
    val moonPay: Boolean = false,
) {

    val isP2P: Boolean
        get() = id == RampPaymentMethod.P2P_ID
}

// [removable] is whether the menu may offer removal: MoonPay allows it and no removal of this card is running.
data class BuyCryptoSavedCard(
    val cardId: String,
    val icon: BuyCryptoCard,
    val expiry: BuyCryptoCardExpiry?,
    val removable: Boolean,
)

// [date] is MM/YY; none for a card with no date that MoonPay no longer takes.
data class BuyCryptoCardExpiry(val date: String?, val state: State) {
    enum class State { Valid, Soon, Expired }
}

interface BuyCryptoMethodViewState : MviViewState {
    val loading: StateFlow<Boolean>
    val failed: StateFlow<Boolean>
    val methods: StateFlow<List<BuyCryptoMethodRow>>
    val openingP2P: StateFlow<Boolean>
    val providerLine: StateFlow<BuyCryptoProviderLine?>
    val providers: StateFlow<List<BuyCryptoProviderRow>>
}

sealed interface BuyCryptoMethodAction : MviAction {
    data class Pick(val id: String) : BuyCryptoMethodAction
    data object Reload : BuyCryptoMethodAction
    data class PickProvider(val provider: ExchangeMerchantSlug) : BuyCryptoMethodAction
    data object RetryProviders : BuyCryptoMethodAction

    // Confirmed by the customer: removes the saved card on MoonPay.
    data class RemoveCard(val cardId: String) : BuyCryptoMethodAction
}

sealed interface BuyCryptoMethodEvent {
    data object Picked : BuyCryptoMethodEvent
    data object ProvidersFailed : BuyCryptoMethodEvent
    data class OpenUrl(val url: String) : BuyCryptoMethodEvent
    data object P2PFailed : BuyCryptoMethodEvent
    data object CardRemoved : BuyCryptoMethodEvent
    data object CardRemovalFailed : BuyCryptoMethodEvent
}

// Shows what «Buy crypto» [buy] decided and sends it every change: it loads nothing of its own and never signs in.
@OptIn(ExperimentalCoroutinesApi::class)
class BuyCryptoMethodFeature(
    private val data: BuyCryptoMethodData,
    private val buy: BuyCryptoOffer,
    private val source: RampSource,
    private val moonPay: MoonPayRamp?,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = LocalDate::now,
) : GraphViewModel<BuyCryptoMethodViewState, BuyCryptoMethodAction>(), BuyCryptoMethodViewState {

    private val relay = MviRelay<BuyCryptoMethodEvent>()
    val events: Flow<BuyCryptoMethodEvent> = relay.events

    private val analytics = BuyCryptoAnalytics(data.analyticsFrom, data.utm)

    init {
        // Covers «Buy crypto», a restored one too, which never showed itself: its failures are told here.
        buy.sendAction(BuyCryptoAction.SetVisible(false))
    }

    private val reloads = actions
        .on<BuyCryptoMethodAction.Reload>()
        .onEach { buy.sendAction(BuyCryptoAction.Reload) }
        .cacheState(initialValue = null)

    private val providerPicks = actions
        .on<BuyCryptoMethodAction.PickProvider>()
        .onEach { buy.sendAction(BuyCryptoAction.SelectProvider(it.provider)) }
        .cacheState(initialValue = null)

    private val providerRetries = actions
        .on<BuyCryptoMethodAction.RetryProviders>()
        .onEach { buy.sendAction(BuyCryptoAction.RetryProviders) }
        .cacheState(initialValue = null)

    override val providers: StateFlow<List<BuyCryptoProviderRow>> = buy.providers

    override val providerLine: StateFlow<BuyCryptoProviderLine?> = buy.providerLine

    private val providerFailures = buy.providerFailures
        .drop(1)
        .onEach { relay.emit(BuyCryptoMethodEvent.ProvidersFailed) }
        .cacheState(initialValue = null)

    override val loading = buy.offer
        .map { it.loading }
        .cacheState(initialValue = true)

    override val failed = buy.offer
        .map { it.failed }
        .cacheState(initialValue = false)

    // Once per opening, at the first list that loads: a rotation or a process death does not show the picker again.
    private val shownTrack = buy.offer
        .mapNotNull { offer -> offer.takeIf { !it.loading && it.methods.isNotEmpty() } }
        .take(1)
        .onEach { offer ->
            if (offer.assetId != null && offer.fiat != null && savedStateHandle.get<Boolean>(KEY_SHOWN_SENT) != true) {
                savedStateHandle[KEY_SHOWN_SENT] = true
                analytics.methodsShown(offer.assetId, offer.fiat, offer.methods)
            }
        }
        .cacheState(initialValue = null)

    private sealed interface Removal {
        val cardId: String

        data class Started(override val cardId: String) : Removal
        data class Ended(override val cardId: String, val removed: Boolean) : Removal
    }

    private data class Removals(
        val running: Set<String> = emptySet(),
        val removed: Set<String> = emptySet(),
    )

    // A second tap on the same card while it is removed asks MoonPay again, which answers an already removed card as removed.
    private val removals = actions
        .on<BuyCryptoMethodAction.RemoveCard>()
        .flatMapMerge { action ->
            flow {
                emit(Removal.Started(action.cardId))
                emit(Removal.Ended(action.cardId, removeCard(action.cardId)))
            }
        }
        .runningFold(Removals()) { removals, removal ->
            when (removal) {
                is Removal.Started -> removals.copy(running = removals.running + removal.cardId)
                is Removal.Ended -> removals.copy(
                    running = removals.running - removal.cardId,
                    removed = removals.removed + listOfNotNull(removal.cardId.takeIf { removal.removed }),
                )
            }
        }
        .cacheState(initialValue = Removals())

    // A removed card leaves the list at once; if «Buy crypto» paid with it, the mark goes to what it will take instead.
    override val methods = combine(buy.offer, removals) { offer, removals ->
        val methods = offer.takeIf { !it.loading }?.methods.orEmpty().filter { it.card?.id !in removals.removed }
        val selected = offer.selected?.takeIf { id -> methods.any { it.id == id } } ?: methods.preferred(offer.selected)?.id
        methods.map { row(it, selected, removals.running) }
    }
        .cacheState(initialValue = emptyList())

    // The first pick closes the picker: a second tap before it is gone is not another pick.
    private val picked = actions
        .on<BuyCryptoMethodAction.Pick>()
        .filter { it.id != RampPaymentMethod.P2P_ID }
        .mapNotNull { pick -> methods.value.firstOrNull { it.id == pick.id && it.enabled } }
        .take(1)
        .onEach { row ->
            val offer = buy.offer.value
            val assetId = offer.assetId
            val fiat = offer.fiat
            if (assetId != null && fiat != null) {
                loaded(row.id)?.let { analytics.methodPicked(assetId, fiat, it) }
                buy.sendAction(BuyCryptoAction.SelectMethod(row.id, fiat, row.title, row.subtitle, row.iconUrl, row.card?.icon, row.moonPay))
            }
            relay.emit(BuyCryptoMethodEvent.Picked)
        }
        .cacheState(initialValue = null)

    private val p2pState = actions
        .on<BuyCryptoMethodAction.Pick>()
        .filter { it.id == RampPaymentMethod.P2P_ID && !p2pOpened.value }
        .transformStateFirst<BuyCryptoMethodAction.Pick, Boolean?> { openP2P() }
        .cacheStateWithEmpty()

    // The market is opening and the sheet is closing: another tap would open a second session.
    override val openingP2P = p2pState
        .isLoading()
        .cacheState(initialValue = false)

    private val p2pOpened: StateFlow<Boolean> = p2pState
        .map { it is KState.Data && it.state == true }
        .cacheState(initialValue = false)

    // Leaving the screen does not stop a removal the customer confirmed; «Buy crypto» learns of it either way.
    private suspend fun removeCard(cardId: String): Boolean {
        val payments = moonPay?.payments ?: return false
        return withContext(NonCancellable) {
            val result = payments.removeCard(cardId)
                .onFailure {
                    verifyError(it)
                    L.e(it)
                }
            if (result.isSuccess) {
                buy.sendAction(BuyCryptoAction.CardsRemoved(setOf(cardId)))
            }
            relay.emit(BuyCryptoMethodEvent.CardRemoved.takeIf { result.isSuccess } ?: BuyCryptoMethodEvent.CardRemovalFailed)
            result.isSuccess
        }
    }

    private fun loaded(id: String): RampPaymentMethod? {
        return buy.offer.value.methods.firstOrNull { it.id == id }
    }

    private suspend fun openP2P(): Boolean {
        val offer = buy.offer.value
        val assetId = offer.assetId ?: return false
        val fiat = offer.fiat ?: return false
        loaded(RampPaymentMethod.P2P_ID)?.let { analytics.methodPicked(assetId, fiat, it) }
        return source.createP2PSessionUrl(assetId, fiat)
            .onSuccess {
                analytics.track(BuyCryptoTrack.ToP2P)
                relay.emit(BuyCryptoMethodEvent.OpenUrl(it))
            }
            .onFailure {
                verifyError(it)
                L.e(it)
                relay.emit(BuyCryptoMethodEvent.P2PFailed)
            }
            .isSuccess
    }

    private fun row(method: RampPaymentMethod, selected: String?, removing: Set<String>): BuyCryptoMethodRow {
        val card = method.card
        return BuyCryptoMethodRow(
            id = method.id,
            title = method.title,
            subtitle = method.subtitle,
            badge = method.badge,
            iconUrl = method.iconUrl,
            selected = method.id == selected,
            enabled = method.selectable && card?.id !in removing,
            moonPay = method.isMoonPay,
            card = card?.let {
                BuyCryptoSavedCard(
                    cardId = it.id,
                    icon = it.icon(),
                    expiry = expiry(it),
                    removable = it.removable && it.id !in removing,
                )
            },
        )
    }

    // As on iOS: expired once its month is over or MoonPay no longer takes it, soon within three months.
    private fun expiry(card: RampSavedCard): BuyCryptoCardExpiry? {
        val month = card.expiryMonth?.takeIf { it in 1..12 }
        val year = card.expiryYear?.let { it.takeIf { it >= CENTURY } ?: (it + CURRENT_CENTURY) }?.takeIf { it in YEARS }
        if (month == null || year == null) {
            return BuyCryptoCardExpiry(date = null, state = BuyCryptoCardExpiry.State.Expired).takeIf { !card.active }
        }
        val date = String.format(Locale.US, "%02d/%02d", month, year % 100)
        val end = LocalDate.of(year, month, 1).plusMonths(1)
        val now = today()
        val state = when {
            !end.isAfter(now) || !card.active -> BuyCryptoCardExpiry.State.Expired
            !end.isAfter(now.plusMonths(SOON_MONTHS)) -> BuyCryptoCardExpiry.State.Soon
            else -> BuyCryptoCardExpiry.State.Valid
        }
        return BuyCryptoCardExpiry(date, state)
    }

    private companion object {
        const val KEY_SHOWN_SENT = "buy_crypto_method_shown_sent"
        const val SOON_MONTHS = 3L
        const val CENTURY = 100
        const val CURRENT_CENTURY = 2000
        val YEARS = 2000..2199
    }
}

/*
 *   Reload, PickProvider, RetryProviders ──► «Buy crypto» [buy]: Reload, SelectProvider, RetryProviders
 *   buy.offer ──┬──► loading, failed
 *               └──► [first loaded, once per opening] shownTrack ──► analytics
 *   buy.providers, buy.providerLine ──► providers, providerLine
 *   buy.providerFailures [a failed retry] ──► providerFailures ──► events
 *   RemoveCard ──► [merge, a removal not cancelled by leaving] removals ──► methods [removed out, running off]
 *   RemoveCard ──► [merge, not cancelled by leaving] removeCard [removed] ──► buy: CardsRemoved; [ended] ──► events
 *   buy.offer, removals ──► methods
 *   Pick [not P2P, enabled, first] ⋯ methods ⋯ buy.offer ──► buy: SelectMethod; events, analytics
 *   Pick [P2P, not opened] ──► [first] p2pState ──► events, analytics
 *   p2pState [loading] ──► openingP2P
 *   p2pState [opened] ──► p2pOpened
 */
