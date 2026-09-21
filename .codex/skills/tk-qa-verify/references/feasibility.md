# Feasibility rubric

Classify each acceptance criterion, then the ticket as a whole. Be concrete: name the screen, the locator, the backend field, the wallet state.

## Criterion classes

| Class | Signs in the ticket / diff | How Claude verifies | Typical cost |
|---|---|---|---|
| **UI on a normal wallet** | new screen, row, toggle, text, navigation | Maestro flow, asserts on text / `id:` | S |
| **Backend-driven state** | refunds, provider errors, limits, balances, flags from `/keys/all`, remote config | Maestro + mitmproxy scenario (`mocks/`) or `featureFlags` / `bootFlags` launch extras for flags | M |
| **Value not rendered** | which provider/aggregator answered, analytics event, request body | Maestro drives UI, `logcat` asserts (`NetLog` logs every HTTP call in debug); `tools/parse_swap_quotes.py` is the pattern | M |
| **Persisted setting** | push toggle, biometry, hidden assets | prefs via `adb shell run-as com.ton_keeper.debug cat shared_prefs/*.xml` (custom `SwitchView` does not expose `checked`) | S |
| **System dialogs** | permission, BiometricPrompt, file picker | Maestro taps the dialog; biometric accept needs `adb emu finger touch 1` between two flows | M |
| **Money movement / signing** | send, swap execution, stake, IAP purchase | quotes and confirm screens automatable; the final confirm only on the team test wallet with explicit approval | M, needs funded wallet |
| **Hardware / other device** | Ledger, Keystone, second phone, pinned home-screen shortcut | manual | — |
| **Visual only** | colour, icon, animation, layout | screenshot review, no assert | S, low confidence |
| **Time / schedule** | "once a day", "after 3 shows", cooldowns | usually manual or needs a device-time trick; say so | L or manual |

## Ticket verdict

- **Automatable** — every acceptance criterion is UI / persisted / value-in-logs on a wallet we have.
- **Automatable with mocks** — at least one criterion needs a backend state we can only get from a fixture; mocks are local-only.
- **Partial** — the core is automatable, but a named criterion needs funds, hardware or a store purchase. List it under "Critical cases not covered" up front.
- **Manual only** — the observable behaviour cannot be produced or asserted from the client (real refund processing, Play Billing, Ledger).

## Estimate

| Size | Meaning |
|---|---|
| **S** ≤ 1 h | existing steps and locators; one new subflow |
| **M** 1–3 h | new flow with 1–2 new locators, or one mock scenario, or a logcat parser |
| **L** ≥ half a day | new device state (biometry enrolment, CA install), several mock scenarios, funded-wallet coordination, or a shared step needs a `testTag` added in the app first |

Add +1 size when the ticket touches a Compose screen whose controls have no `testTag` (position taps are allowed only in `*_local/` flows and must be flagged for a follow-up).

## Adjacent areas from the PR

For each changed path decide who else reads it:

- `apps/wallet/data/*` repository or mapper → every screen that consumes it gets one smoke check (home widget, details screen, settings).
- `apps/wallet/api/` or `tonapi/*` model → the request/response shape changed; check one flow that sends it and one that renders it.
- `features/core` deeplinks, `RootActivity`, `RootViewModel` → open one deeplink and one push-style intent.
- `lib/features`, `WalletFeatureKey`, `RemoteConfig` → run the main case with the flag on and off.
- `localization/` → one screenshot in the changed locale.
- `instance/app` Fragment screens → they still exist next to Compose ones; check both entry points when the same feature has two.
