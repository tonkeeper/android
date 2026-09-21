# MVI

Kotlin Multiplatform module for screen ViewModels. The current approach is the **property graph**:
a feature is a directed graph of `StateFlow` nodes built with `GraphViewModel`
(`com.tonapps.mvi.graph`).

## ⚠️ Deprecated: `MviFeature` and everything connected to it

The old single-state stack is deprecated. Do not use it for new screens; extend
`com.tonapps.mvi.graph.GraphViewModel` instead.

| Deprecated                          | Replacement                                              |
|-------------------------------------|----------------------------------------------------------|
| `MviFeature`                        | `GraphViewModel`                                         |
| `MviFeatureDelegate`                | Nodes are already small; split by node, not by delegate  |
| `MviBinder`, `buildViewState { }`   | Each node is a `StateFlow` created with `cacheState`     |
| `MviProperty`, `MviPropertyLiveData`, `observeSafeState()` | Plain `StateFlow` + `collectAsState()` |
| `ChangeStrategy`                    | `StateFlow` equality + `distinctUntilChanged`            |
| `MviState`, `Stateful`, `setState`  | Per-node values; reducers via `runningFold`              |
| `MviSubject`                        | A node fed from the action bus                           |

Still current and shared by both worlds:

| Component                  | Purpose                                                     |
|----------------------------|-------------------------------------------------------------|
| `AsyncViewModel`           | Scopes (`mainScope`, `bgScope`, `stateScope`), base class   |
| `MviRelay`                 | One-shot events ViewModel → Compose (navigation, toasts)    |
| `MviAction`, `MviViewState`| Contract markers; `GraphViewModel` bounds its type params with them |
| `Mvi.init(Mvi.Config(...))`| `isFastFail` (throw vs log on violations), `useThreadCheck` |
| `com.tonapps.mvi.flow.*`   | `withLatestFrom`, `countdown`, `transformFirst`, `noneOfFlows`, `anyOfFlows` |

## The property graph

A feature is a set of nodes. Each node is one immutable value exposed as a `StateFlow`, produced
by a cold flow chain and cached with `cacheState` (eager, on `mainScope`). Nodes are wired only
through their flows:

- **driving input** — `combine`: any emission recomputes the node;
- **sampled input** — `withLatestFrom`: declared dependency that never triggers the node;
- **back edge** — closed through the action bus with `sendAction(...)`, never by calling
  another node directly.

The UI talks to the graph only via `sendAction` and reads only `StateFlow`s.

```kotlin
class MyFeature : GraphViewModel<MyState, MyAction>(), MyState {
    //                            └ : MviViewState  └ : MviAction
}
```

## Showcase

A complete "sell" feature: initial asset load, debounced amount parsing, quote fetching with a
TTL countdown and requote, and a continue action that must not race the timer. Domain types
(`Asset`, `CoinValue`, `Repository`) are fakes — the graph is the point.

```kotlin
data class Asset(val id: String)

data class CoinValue(val value: Long) {
    val isPositive: Boolean get() = value > 0
}

data class Quote(
    val id: String,
    val asset: Asset,
    val price: Int,
)

data class BuyTransaction(
    val data: String,
)

enum class AmountError
enum class QuoteError

interface Repository {
    suspend fun loadAsset(assetId: String): Asset?
    suspend fun loadQuote(assetId: String, value: CoinValue): KResult<Quote, QuoteError>?
    suspend fun loadTx(quoteId: String): BuyTransaction?
    fun amountParser(text: String): KResult<CoinValue, AmountError>?
}

interface SellState : MviViewState {
    val assetLoading: StateFlow<Boolean>
    val asset: StateFlow<Asset?>
    val amount: StateFlow<KResult<CoinValue, AmountError>?>

    val quoteLoading: StateFlow<Boolean>
    val quote: StateFlow<KResult<Quote, QuoteError>?>
    val quoteTimer: StateFlow<Float?>

    val continueEnabled: StateFlow<Boolean>
    val continueLoading: StateFlow<Boolean>
}

sealed interface SellAction : MviAction {
    data class SetAmount(val value: String) : SellAction
    data class SelectAsset(val value: Asset) : SellAction
    object Continue : SellAction
    object Requote : SellAction
    object StartTimer : SellAction
    object StopTimer : SellAction
}

class SellFeature(
    private val initialAssetId: String,
    private val repo: Repository,
) : GraphViewModel<SellState, SellAction>(), SellState {

    companion object {
        private val DEFAULT_ASSET = Asset(id = "ton")
        private val QUOTE_TTL = 60.seconds
    }

    private val relay = MviRelay<String>()
    val events = relay.events

    // Asset
    private val initAssetState = flowOf(initialAssetId)
        .transformStateLatest { item ->
            runCatching { repo.loadAsset(item) }
                .getOrNull() ?: DEFAULT_ASSET
        }
        .cacheStateWithLoading()

    override val assetLoading = initAssetState
        .isLoading()
        .cacheState(initialValue = true)

    override val asset = combine(
        initAssetState
            .mapStateDataOrNull(),
        actions
            .on<SellAction.SelectAsset>()
            .cacheState(initialValue = null),
        transform = { init, selected -> selected?.value ?: init?.state }
    )
        .cacheState(initialValue = null)

    // Amount
    private val amountState = actions
        .on<SellAction.SetAmount>()
        .transformStateLatest {
            delay(500L)
            repo.amountParser(it.value)
        }
        .cacheStateWithLoading()

    private val amountLoading = amountState.isLoading()

    override val amount = amountState
        .mapStateDataValueOrNull()
        .mapResultValueOrNull()
        .cacheState(initialValue = null)

    // Quote
    private data class QuoteContext(
        val asset: Asset,
        val amount: CoinValue,
        val attempt: Int,
    )

    private val requoteAttempt = actions
        .on<SellAction.Requote>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val quoteState = combine(
        asset,
        amount,
        requoteAttempt,
        transform = { asset, amount, attempt ->
            val asset = asset ?: return@combine null
            val amount = amount?.value ?: return@combine null

            QuoteContext(asset, amount, attempt)
        }
    )
        .distinctUntilChanged()
        .transformStateLatest { ctx ->
            val ctx = ctx
                ?: return@transformStateLatest null

            runCatching { repo.loadQuote(ctx.asset.id, ctx.amount) }
                .getOrNull()
        }
        .cacheStateWithLoading()

    override val quoteLoading = quoteState
        .isLoading()
        .cacheState(initialValue = false)

    override val quote = quoteState
        .mapStateDataValueOrNull()
        .cacheState(initialValue = null)

    // Continue
    private val continueState = actions
        .on<SellAction.Continue>()
        .withLatestFrom(quote) { _, quote -> quote }
        .transformStateFirst { quote ->
            val quote = quote as? KResult.Ok
                ?: return@transformStateFirst null

            runCatching { repo.loadTx(quote.value.id) }
                .fold(
                    onSuccess = { relay.emit("navigate_success") },
                    onFailure = { relay.emit("show_error") }
                )
        }
        .cacheStateWithEmpty()

    override val continueLoading = continueState.isLoading()
        .cacheState(initialValue = false)

    override val continueEnabled = combine(
        noneOfFlows(assetLoading, amountLoading, quoteLoading, continueLoading),
        amount.mapResultValueOrNull(),
        quote.mapResultValueOrNull(),
        transform = { isLoaded, amount, quote ->
            val hasAmount = amount?.value.isPositive
            val hasQuote = quote != null
            isLoaded && hasAmount && hasQuote
        }
    )
        .cacheState(initialValue = false)

    // Timer
    private val timerPaused = actions
        .filter { it is SellAction.StartTimer || it is SellAction.StopTimer }
        .map { it is SellAction.StopTimer }
        .cacheState(initialValue = false)

    private val timerRunning = noneOfFlows(timerPaused, continueLoading)
        .cacheState(initialValue = true)

    private val quoteCountdown = quote
        .mapResultValueOrNull()
        .countdown(QUOTE_TTL, running = timerRunning)
        .onEach { countdown ->
            if (countdown != null && countdown.finished) {
                sendAction(SellAction.Requote)
            }
        }
        .cacheState(initialValue = null)

    override val quoteTimer = quoteCountdown
        .map { it?.progress }
        .cacheState(initialValue = null)
}
```

### Graph diagram

Document every feature with this diagram. `?` marks a node with a nullable value, a repeated name
is the same node, `⋯` is a sample (`withLatestFrom`: a declared input that never drives the
output) and `╌►` is a back edge, closed through the action bus. An operator list `[…]` names
operators in application order, and its position says where they run: **before a node name**
(after the arrow) it produces that node from its (joined) input
(`──► [distinct, latest] quoteState`); **after a node reference** it is applied to that flow
where it is consumed (`amountState [dataOrNull, okOrNull] ──►`). A `[…]` without a name is an
unnamed intermediate flow inside a join. Fan-out needs no brackets — a projection line just
repeats the node name. An unlabeled edge is a plain `map` / `filter`.

| `[…] name` — produces the node | Operator |
|---|---|
| `latest` / `first` | `transformStateLatest { }` / `transformStateFirst { }` |
| `fold` | `runningFold(initial, ::reduce)` — reducer node |
| `countdown` | `countdown(total, running = …)` |
| `none` / `any` | `noneOfFlows(...)` / `anyOfFlows(...)` |
| `debounce` | leading `delay(...)` inside the loader (or `debounce(...)`) |
| `distinct` | `distinctUntilChanged()` on the joined input |

| `name […]` — applied where consumed | Operator |
|---|---|
| `loading` | `isLoading()` |
| `loadingOrNull` | `mapStateLoadingOrNull()` |
| `dataOrNull` | `mapStateDataValueOrNull()` / `mapStateDataOrNull()` |
| `dataOnly` | `filterAndMapStateData()` / `filterStateData()` |
| `okOrNull` / `okOnly` | `mapResultValueOrNull()` / `filterResultValue()` |
| `errOrNull` / `errOnly` | `mapResultErrorOrNull()` / `filterResultError()` |
| `distinct` | `distinctUntilChanged(By …)` at the consumption site |

```
           initialAssetId ──► [latest] initAssetState
 initAssetState [loading] ──► assetLoading

 initAssetState [dataOrNull] ─────────┐
                                      ├──► asset?
       SelectAsset ──► selectedAsset? ┘


                          SetAmount ──► [debounce, latest] amountState
              amountState [loading] ──► amountLoading
 amountState [dataOrNull, okOrNull] ──► amount?


                            asset? ──┐
                           amount? ──┼──► [distinct, latest] quoteState
 Requote ──► [fold] requoteAttempt ──┘
              quoteState [loading] ──► quoteLoading
           quoteState [dataOrNull] ──► quote?


                 Continue ──┐
                            ├──► [first] continueState?
                quote? ⋯────┘
 continueState? [loading] ──► continueLoading
           continueState? ──► events


 StartTimer/StopTimer ──► timerPaused ──┐
                                        ├──► [none] timerRunning
                      continueLoading ──┘

      timerRunning ──┐
                     ├──► [countdown] quoteCountdown? ──► quoteTimer?
 quote? [okOrNull] ──┘                       ╎
                                             ╎
            Requote ◄╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘


       assetLoading ──┐
      amountLoading ──┤
       quoteLoading ──┼─ [none] ─┐
    continueLoading ──┘          │
                                 ├──► continueEnabled
       amount? [okOrNull] ───────┤
        quote? [okOrNull] ───────┘
```

## Rules

### Threading — the load-bearing invariant

Every node caches on the default `mainScope` (`Dispatchers.Main.immediate`) and `sendAction`
must be called from the main thread (`GraphViewModel` asserts it; `transformState*` assert too).
The whole graph is serialized on one thread and emissions propagate before the next input event,
which is what closes every sampling window: "requote can't race continue", "a tap can't see a
stale enabled-state", "typing can't drop characters".

- ✅ `cacheState(...)` with the default scope everywhere.
- ❌ `cacheState(scope = stateScope)`, `flowOn(...)`, buffers, or off-main `sendAction` anywhere
  in a node chain — each one silently reopens a race window.

### Nodes and state

- ✅ One `StateFlow` per value the UI (or another node) needs.
- ❌ One merged `State` data class with a single reducer — that is the deprecated approach; solve
  cross-node agreement by enriching node values, not by merging nodes.
- ✅ Values that must change together live in **one** node with a reducer. In the swap feature
  sell/buy assets are one `AssetPair` node, so "picking the asset that already sits on the other
  side swaps them" can never collide.
- ✅ Reducers are pure: `runningFold(initial, ::reduce)`. Commands are a per-node sealed
  interface — UI actions arrive wrapped (`Cmd.Action`), internal edges get their own commands
  (`Cmd.Reset`, `Cmd.ClearMax`) and are merged in with `merge(...)`.
- ❌ `var` fields, `MutableStateFlow`, or any mutable state shared between nodes. All state is
  node values.
- ✅ Declare nodes top-down; a node may only reference properties declared **above** it (Kotlin
  initialization order — a forward reference is a NPE at construction). A cycle must go through
  the action bus.

### Edges

- ✅ `combine` when the input should recompute the node; `withLatestFrom` when it should only be
  sampled. Getting this wrong either causes feedback loops (`combine` where a sample was meant)
  or missed updates (`withLatestFrom` where a driver was meant).
- ✅ Back edges carry **triggers, never data**. A payload computed across a suspension is a stale
  sample by the time it lands. Instead of `sendAction(Recalculate(newText))` after a load, send a
  payload-free `Recalculate`, count it into an attempt node, and make the attempt an input of the
  node that owns the value — the node re-samples everything inside its own `transformStateLatest`,
  so a late trigger is at worst redundant, never wrong.
- ✅ One value — one flow. Two flows that can emit the same value concurrently are a race by
  construction: whichever loader finishes last wins, regardless of which input is newer.
  `transformStateLatest` cancels only within its own branch, so never fan out by input kind and
  `merge` the results — branch **inside** a single loader:

  ```kotlin
  // ❌ parallel writers: a slow ENS resolve started earlier lands after —
  // and overwrites — a newer plain-address verification; the Address
  // action does not cancel the in-flight Ens branch
  val input = actions.on<Action.Input>().transformStateLatest { toType(it) }
  val fromAddress = actions.on<Type.Address>().transformStateLatest { verifyAddress(it) }
  val fromEns = actions.on<Type.Ens>().transformStateLatest { verifyAddress(resolveDns(it)) }
  val address = merge(fromAddress, fromEns).cacheStateWithLoading()

  // ✅ one node: any new input cancels whichever variant is in flight
  val address = actions.on<Action.SetAddress>()
      .transformStateLatest { action ->
          when (val input = toInputType(action.text)) {
              is Input.Ens -> verifyAddress(resolveDns(input))
              is Input.Address -> verifyAddress(input)
          }
      }
      .cacheStateWithLoading()
  ```
- ⚠️ Reducer arbitration — echoing provenance (source input, generation) in a command so the
  reducer can drop stale results — is a last resort for a value that genuinely has two sources.
  Prefer restructuring so it has one: route every producer through the single node that owns the
  value.
- ✅ `distinctUntilChanged()` on a composed context before an expensive loader — `combine`
  re-emits when any input changes, including changes that produce an equal context.
- ✅ Debounce expensive loaders: `debounce(...)` on the context flow, or — preferably — a leading
  `delay(...)` inside `transformStateLatest`. The loader emits `KState.Loading` before the delay,
  so the graph reacts immediately (spinner on, stale value invalidated, dependents disabled)
  while the fetch waits out the typing burst; `debounce` keeps the node silent for the whole
  window with the stale value still current.

### Loaders

- ✅ `transformStateLatest` wraps any async work in a node, one-shot included
  (`flowOf(assetId).transformStateLatest { loadAsset(it) }`): emits `Loading` first, `Data` on
  completion; a re-emitting input cancels in-flight work; a leading `delay(...)` doubles as a
  debounce.
- ✅ `transformStateFirst` for one-shot actions that must drop re-entrant triggers (double-tap on
  Continue) while in flight.
- ✅ Loaders catch their own errors and **return a value** (fallback, `null`, `KResult.Err`).
  The `onFailure` branch of `transformState*` only logs — a loader that throws leaves the node
  stuck in `Loading` forever (spinner on, button disabled, no retry).
- ✅ When you do catch broadly, call `verifyError(t)` first so `CancellationException` is
  rethrown and cancellation is not swallowed.
- ⚠️ `KState.Loading` carries the previous value (`runningReduce`), so the UI can keep showing
  stale data while reloading. That is for **display only** — never feed the carried value into
  money math or decisions (e.g. a MAX button must not compute from the previous asset's balance;
  sample the `KState` and require `KState.Data`).
- ✅ Chained loaders — invalidation propagates only through links that emit:

  ```kotlin
  // ✅ propagating: Loading(prev) is an emission — quote restarts, resets to null
  val quote = accountState.transformStateLatest {
      fetchQuote((it as? KState.Data)?.state ?: return@transformStateLatest null)
  }

  // ❌ breaking: it.state holds the stale value → dedupe → quote never restarts,
  // keeps the old quote while the account reloads
  val quote = accountState.map { it.state }.transformStateLatest {
      fetchQuote(it ?: return@transformStateLatest null)
  }
  ```

- ✅ A chain's loading flag always ORs every stage. The last stage alone is never enough —
  during the upper stage's reload the lower loader instantly returns `null`, so
  `quoteLoading == false` while the account is still loading:

  ```kotlin
  val accountLoading = accountState.isLoading()
  val quoteLoading = quoteState.isLoading()

  // ❌ false while the account reloads — quote already reset to Data(null)
  val isLoading = quoteLoading

  // ✅ covers every stage of the chain
  val isLoading = anyOfFlows(accountLoading, quoteLoading)
  ```

### Events and UI

- ✅ One-shot events (navigation, toasts) via `MviRelay`, collected in a single `LaunchedEffect`.
  State never goes through the relay; events never go through nodes.
- ✅ Compose reads nodes with `collectAsState()` and sends `sendAction(...)` from click handlers —
  nothing else crosses the boundary.
- ✅ The feature implements its `State` interface directly
  (`class SellFeature : GraphViewModel<SellState, SellAction>(), SellState`), so the screen
  receives one object.

## Building blocks

### `com.tonapps.mvi.graph`

| Type / operator | Purpose |
|---|---|
| `GraphViewModel<State : MviViewState, Action : MviAction>` | Base class: action bus, `sendAction`, state operators |
| `KState<T>` (`Loading(prev)` / `Data`) | Async node value; `Loading` keeps the previous value |
| `KResult<T, E>` (`Ok` / `Err`) | Domain result inside a node value |
| `transformStateLatest { }` | Restartable loader → `Flow<KState<R>>` |
| `transformStateFirst { }` | Non-reentrant loader → `Flow<KState<R>>` |
| `cacheState(initialValue)` / `cacheStateWithLoading()` / `cacheStateWithEmpty()` | Node caching (eager, `mainScope`) |
| `isLoading()`, `filterAndMapStateData()`, `mapStateDataValueOrNull()`, … | `KState` operators |
| `mapResultValueOrNull()`, `filterResultError()`, … | `KResult` operators |

### `com.tonapps.mvi.flow`

| Operator | Purpose |
|---|---|
| `withLatestFrom(other) { a, b -> }` | Sample a `StateFlow` without being driven by it |
| `countdown(total, running = …)` | TTL timer per upstream value; pausable; `finished` closes back edges |
| `transformFirst { }` | Drop upstream values while a transform is in flight |
| `noneOfFlows(vararg)` / `anyOfFlows(vararg)` | Combine boolean nodes |
