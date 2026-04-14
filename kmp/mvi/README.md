# MVI Architecture

Custom MVI (Model-View-Intent) framework built as a Kotlin Multiplatform module. Provides
unidirectional data flow with thread-safe state management for Jetpack Compose.

## Data Flow

```
User Interaction (Compose)
    ↓
sendAction(Action)              — any thread
    ↓
executeAction(action)           — StateThread
    ↓
setState { copy(...) }          — any thread → dispatched to StateThread
    ↓
MviBinder.update(state)         — ComputationThread (state diff)
    ↓
MviProperty.setValue(value)     — MainThread
    ↓
observeSafeState() → recomposition
```

## Core Components

### Contracts

| Interface      | Purpose                                                                         |
|----------------|---------------------------------------------------------------------------------|
| `MviAction`    | Marker for user intents / events                                                |
| `MviState`     | Marker for internal data state (typically `data class` or `sealed interface`)   |
| `MviViewState` | Marker for view-layer state, annotated `@Stable`, contains `MviProperty` fields |

### MviFeature

Abstract base class for screen ViewModels. Extends `AsyncViewModel` (which provides coroutine
scopes).

```kotlin
abstract class MviFeature<A : MviAction, S : MviState, VS : MviViewState>(
    initState: S,
    initAction: A? = null
) : AsyncViewModel(), Stateful<S>
```

**Key methods:**

| Method                   | Thread      | Purpose                                 |
|--------------------------|-------------|-----------------------------------------|
| `sendAction(action)`     | Any         | Dispatch an action from UI              |
| `executeAction(action)`  | StateThread | Handle the action (abstract)            |
| `createViewState()`      | StateThread | Map state → view state (abstract)       |
| `buildViewState { }`     | MainThread  | DSL to bind MviProperty to state fields |
| `setState { copy(...) }` | Any         | Update internal state                   |
| `obtainState()`          | StateThread | Read current state                      |

### MviFeatureDelegate

Splits action handling into separate classes. Has access to `mainScope`, `bgScope`, `setState`,
`obtainState`.

```kotlin
abstract class MviFeatureDelegate<A : MviAction, S : MviState>(
    feature: MviFeature<in A, S, *>
) : Stateful<S>
```

### MviProperty

Reactive property for Compose observation. Backed by `StateFlow`.

```kotlin
// In ViewState
class ViewState(
    val title: MviProperty<String>,
    val items: MviProperty<List<Item>>,
) : MviViewState

// In Compose
val title by feature.state.title.observeSafeState()
val items by feature.state.items.observeSafeState()
```

### MviRelay

One-shot event channel from ViewModel to Compose (navigation, toasts, etc.). Channel-based, capacity
100, drops oldest.

```kotlin
// In Feature
private val _events = MviRelay<Event>()
val events: Flow<Event> = _events.events

// Emit
_events.emit(Event.NavigateBack)

// In Compose
LaunchedEffect(Unit) {
    feature.events.collect { event ->
        when (event) {
            Event.NavigateBack -> navController.popBackStack()
        }
    }
}
```

### MviSubject

SharedFlow-based data channel with replay (for search queries, filters, etc.).

```kotlin
private val query = MviSubject<String>()

// Emit
query.emit(searchText)

// Observe
query.events.debounce(300).collect { q -> search(q) }
```

## Threading Model

Four dedicated dispatchers managed by `AsyncViewModel`:

| Scope            | Dispatcher                     | Usage                                      |
|------------------|--------------------------------|--------------------------------------------|
| `bgScope`        | `Async.Io`                     | Network, DB, file I/O                      |
| `stateScope`     | `Async.stateDispatcher()`      | State mutations (pool of 3 threads)        |
| `mainScope`      | `Dispatchers.Main.immediate`   | UI updates                                 |
| `stateDiffScope` | `Async.stateDiffDispatchers()` | State diff computation (pool of 2 threads) |

Thread annotations: `@StateThread`, `@MainThread`, `@ComputationThread`, `@BgThread`.

Runtime thread checking is configurable:

```kotlin
Mvi.init(Mvi.Config(useThreadCheck = true, isFastFail = false))
```

## Change Strategies

Control when `MviProperty` emits updates:

| Strategy                 | Comparison     | Use case                 |
|--------------------------|----------------|--------------------------|
| `ChangeStrategy.Value()` | `==` (default) | Data classes, primitives |
| `ChangeStrategy.Ref()`   | `===`          | Object identity          |
| `ChangeStrategy.Hash()`  | `hashCode()`   | Custom equality          |

```kotlin
mviProperty(strategy = ChangeStrategy.Ref()) { state -> state.items }
```

## Full Example

### 1. Define contracts

```kotlin
sealed interface Action : MviAction {
    data class Init(val userId: String) : Action
    data class Delete(val itemId: String) : Action
}

sealed interface State : MviState {
    data object Loading : State
    data class Error(val error: Throwable) : State
    data class Data(val items: List<Item>) : State
}

class ViewState(
    val isLoading: MviProperty<Boolean>,
    val error: MviProperty<Throwable?>,
    val title: MviProperty<String>,
    val items: MviProperty<List<Item>>,
) : MviViewState
```

### 2. Create the Feature

```kotlin
class UserFeature(
    private val userRepo: UserRepo,
) : MviFeature<Action, State, ViewState>(
    initState = State.Loading
) {

    private val deleteDelegate = DeleteDelegate(this, userRepo)

    override fun createViewState(): ViewState {
        return buildViewState {
            ViewState(
                isLoading = mviProperty { it is State.Loading },
                error = mviProperty { (it as? State.Error)?.error },
                title = mviProperty {
                    if (it is State.Loading) "Loading..." else "Users"
                },
                items = mviProperty {
                    (it as? State.Data)?.items.orEmpty()
                },
            )
        }
    }

    override suspend fun executeAction(action: Action) {
        when (action) {
            is Action.Init -> loadUsers(action.userId)
            is Action.Delete -> deleteDelegate.execute(action)
        }
    }

    private suspend fun loadUsers(userId: String) {
        setState { State.Loading }
        try {
            val items = withContext(bgScope.coroutineContext) {
                userRepo.getItems(userId)
            }
            setState { State.Data(items) }
        } catch (e: Exception) {
            setState { State.Error(e) }
        }
    }
}
```

### 3. Create a delegate (optional)

```kotlin
class DeleteDelegate(
    feature: UserFeature,
    private val userRepo: UserRepo,
) : MviFeatureDelegate<Action.Delete, State>(feature) {

    override suspend fun executeAction(action: Action.Delete) {
        try {
            withContext(bgScope.coroutineContext) {
                userRepo.delete(action.itemId)
            }
            // setState with type constraint — only runs if current state is State.Data
            setState<State.Data> {
                copy(items = items.filter { it.id != action.itemId })
            }
        } catch (e: Exception) {
            setState { State.Error(e) }
        }
    }
}
```

### 4. Use in Compose

```kotlin
@Composable
fun UserScreen(feature: UserFeature) {
    // Trigger initial load once
    OnceOnly {
        feature.sendAction(Action.Init("user-123"))
    }

    val isLoading by feature.state.isLoading.observeSafeState()
    val error by feature.state.error.observeSafeState()

    when {
        isLoading -> LoadingIndicator()
        error != null -> ErrorView(error!!)
        else -> UserList(feature.state) { itemId ->
            feature.sendAction(Action.Delete(itemId))
        }
    }
}

@Composable
fun UserList(viewState: ViewState, onDelete: (String) -> Unit) {
    val items by viewState.items.observeSafeState()
    val title by viewState.title.observeSafeState()

    Text(title)
    LazyColumn {
        items(items.size) { index ->
            ItemRow(items[index], onDelete = { onDelete(items[index].id) })
        }
    }
}
```

## Source Files

```
kmp/mvi/src/commonMain/kotlin/com/tonapps/
├── mvi/
│   ├── MviFeature.kt              # Core feature base class
│   ├── MviFeatureDelegate.kt      # Delegate for action splitting
│   ├── MviBinder.kt               # State → MviProperty binding
│   ├── AsyncViewModel.kt          # ViewModel with coroutine scopes
│   ├── MviRelay.kt                # One-shot event channel
│   ├── MviSubject.kt              # SharedFlow data channel
│   ├── Mvi.kt                     # Global configuration
│   ├── ChangeStrategy.kt          # Value/Ref/Hash comparison
│   ├── Initializer.kt             # OnceOnly composable
│   ├── contract/
│   │   ├── MviAction.kt
│   │   ├── MviState.kt
│   │   ├── MviViewState.kt
│   │   └── internal/Stateful.kt   # State access interface
│   ├── props/
│   │   ├── MviProperty.kt         # Read-only property interface
│   │   └── MviPropertyLiveData.kt # Implementation + observeSafeState()
│   └── thread/
│       ├── MainThread.kt
│       ├── StateThread.kt
│       ├── ComputationThread.kt
│       ├── BgThread.kt
│       └── MviThread.kt           # Runtime thread validation
```
